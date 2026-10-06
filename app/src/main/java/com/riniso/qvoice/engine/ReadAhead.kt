package com.riniso.qvoice.engine

import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Runs one synthesis on its own thread, ahead of playback, and hands the
 * audio to the calling thread in order.
 *
 * Why (found in the 0.2.2 log from the Galaxy M31): Android's playback queue
 * blocks SynthesisCallback.audioAvailable() while more than about half a
 * second of audio is waiting to play (SynthesisPlaybackQueueItem,
 * MAX_UNCONSUMED_AUDIO_MS = 500). When QVoice generated on the framework's
 * synthesis thread, each sentence's audio could only be handed over as fast
 * as it played, so the next sentence started generating about 0.5 s before
 * the current one ended — every sentence that took longer than that to
 * generate left a gap, even with a voice five times faster than real time
 * (Kristin: a 10-second sentence takes ~2 s, a 1.5 s gap). Here the producer
 * thread keeps generating into a queue of up to [maxAheadSamples] while the
 * calling thread feeds the framework at playback speed.
 *
 * Stopping: [cancel] (from onStop, any thread) makes the producer's sink
 * return false, which tells sherpa-onnx to stop after the sentence it is
 * generating; there is no way to interrupt a sentence midway. [run] returns
 * only after the producer thread has ended — the model it uses may be
 * released as soon as [run] returns (EngineHost's lock is held around it).
 *
 * One instance per synthesis; [run] may be called once.
 */
class ReadAhead(private val maxAheadSamples: Int) {

    /**
     * What one run produced. [synthMillis] is the producer's working time:
     * generating, minus the time it waited for the listener to catch up —
     * the number that says whether a voice is fast enough for this phone.
     */
    class Result(val synthMillis: Long, val samples: Long, val cancelled: Boolean)

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    // All guarded by [lock].
    private val queue = ArrayDeque<FloatArray>()
    private var queuedSamples = 0L
    private var producedSamples = 0L
    private var waitingNanos = 0L
    private var synthNanos = 0L
    private var started = false
    private var finished = false
    private var cancelled = false
    private var failure: Throwable? = null

    init {
        require(maxAheadSamples > 0) { "maxAheadSamples must be positive" }
    }

    /** Stops the run as soon as possible. Safe from any thread, before or during [run]. */
    fun cancel() {
        lock.withLock {
            cancelled = true
            changed.signalAll()
        }
    }

    /**
     * @param produce runs on a new thread. It calls its argument with each
     *   chunk of audio, in order; the argument returns false once the run is
     *   cancelled, and [produce] should then return (sherpa-onnx does when
     *   its callback returns 0).
     * @param consume runs on the calling thread for each chunk, in order, and
     *   may block (audioAvailable does). Returning false cancels the run.
     * @throws Throwable whatever [produce] threw, after the producer has ended.
     */
    fun run(produce: ((FloatArray) -> Boolean) -> Unit, consume: (FloatArray) -> Boolean): Result {
        lock.withLock {
            check(!started) { "ReadAhead.run may be called once" }
            started = true
            // Stopped before it began: don't generate even one sentence.
            if (cancelled) return Result(0L, 0L, cancelled = true)
        }
        val producer = Thread({ produceSafely(produce) }, "qvoice-synth")
        producer.start()
        var drained = false
        try {
            while (true) {
                val chunk = take()
                if (chunk == null) {
                    drained = true
                    break
                }
                if (!consume(chunk)) break
            }
        } finally {
            // The listener stopped (or threw): release a producer waiting for room.
            if (!drained) cancel()
            joinUninterruptibly(producer)
        }
        lock.withLock {
            failure?.let { throw it }
            return Result(synthNanos / NANOS_PER_MILLI, producedSamples, cancelled)
        }
    }

    private fun produceSafely(produce: ((FloatArray) -> Boolean) -> Unit) {
        val start = System.nanoTime()
        try {
            produce { chunk -> offer(chunk) }
        } catch (t: Throwable) {
            // Rethrown on the calling thread by run(); nothing may escape a
            // thread on Android (the default handler kills the process).
            lock.withLock { failure = t }
        } finally {
            lock.withLock {
                synthNanos = (System.nanoTime() - start - waitingNanos).coerceAtLeast(0L)
                finished = true
                changed.signalAll()
            }
        }
    }

    /** Producer side: queues [chunk], waiting while the listener is far enough behind. */
    private fun offer(chunk: FloatArray): Boolean {
        lock.withLock {
            val waitStart = System.nanoTime()
            // A chunk larger than the whole allowance still goes into an empty
            // queue; refusing it would deadlock.
            while (!cancelled && queue.isNotEmpty() && queuedSamples + chunk.size > maxAheadSamples) {
                changed.awaitUninterruptibly()
            }
            waitingNanos += System.nanoTime() - waitStart
            if (cancelled) return false
            queue.addLast(chunk)
            queuedSamples += chunk.size
            producedSamples += chunk.size
            changed.signalAll()
            return true
        }
    }

    /** Listener side: the next chunk, or null when the run is over (finished, cancelled or failed). */
    private fun take(): FloatArray? {
        lock.withLock {
            while (queue.isEmpty() && !finished && !cancelled && failure == null) changed.awaitUninterruptibly()
            if (cancelled || failure != null) return null
            val chunk = queue.pollFirst() ?: return null
            queuedSamples -= chunk.size
            changed.signalAll()
            return chunk
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        /**
         * Never returns while the producer runs, even if interrupted: the
         * caller releases the engine lock next, and a model released while
         * the producer is still inside it is a native use-after-free.
         */
        fun joinUninterruptibly(thread: Thread) {
            var interrupted = false
            while (true) {
                try {
                    thread.join()
                    break
                } catch (e: InterruptedException) {
                    interrupted = true
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}
