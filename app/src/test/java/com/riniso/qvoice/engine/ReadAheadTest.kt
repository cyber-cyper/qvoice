package com.riniso.qvoice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ReadAheadTest {

    private fun chunk(n: Int, value: Float = 0f) = FloatArray(n) { value }

    @Test
    fun deliversEveryChunkInOrder() {
        val got = ArrayList<Float>()
        val result = ReadAhead(maxAheadSamples = 100).run(
            produce = { sink -> for (i in 1..10) sink(chunk(3, i.toFloat())) },
            consume = { samples -> got += samples[0]; true },
        )
        assertEquals((1..10).map { it.toFloat() }, got)
        assertEquals(30L, result.samples)
        assertFalse(result.cancelled)
    }

    /**
     * The point of the class: generation doesn't wait for playback. The
     * listener blocks inside its FIRST chunk until the producer has made all
     * five — a synchronous implementation (the 0.2.2 behaviour) can't get
     * there and fails on the timeout.
     */
    @Test
    fun producerRunsAheadOfTheListener() {
        val allProduced = CountDownLatch(1)
        var first = true
        ReadAhead(maxAheadSamples = 1_000).run(
            produce = { sink ->
                repeat(5) { sink(chunk(10)) }
                allProduced.countDown()
            },
            consume = {
                if (first) {
                    first = false
                    assertTrue("producer waited for playback", allProduced.await(5, TimeUnit.SECONDS))
                }
                true
            },
        )
    }

    /** No more than maxAheadSamples wait in the queue (plus the chunk being played). */
    @Test
    fun queueIsBounded() {
        val accepted = AtomicInteger()
        var checked = false
        ReadAhead(maxAheadSamples = 10).run(
            produce = { sink ->
                repeat(6) { if (sink(chunk(4))) accepted.incrementAndGet() }
            },
            consume = {
                if (!checked) {
                    checked = true
                    Thread.sleep(300) // time for a broken producer to overfill the queue
                    // Chunk 1 is being played; 2 and 3 (8 samples) are queued;
                    // 4 would make 12 > 10, so the producer is waiting.
                    assertEquals(3, accepted.get())
                }
                true
            },
        )
        assertEquals(6, accepted.get())
    }

    @Test
    fun chunkLargerThanTheAllowanceStillGoesThrough() {
        val result = ReadAhead(maxAheadSamples = 10).run(
            produce = { sink -> sink(chunk(50)); sink(chunk(50)) },
            consume = { true },
        )
        assertEquals(100L, result.samples)
    }

    /** Listener says stop: the producer is told at its next chunk and has ended when run returns. */
    @Test
    fun listenerStopEndsTheProducerBeforeRunReturns() {
        val producerEnded = AtomicBoolean(false)
        val refusedAt = AtomicInteger(-1)
        var consumed = 0
        val result = ReadAhead(maxAheadSamples = 8).run(
            produce = produce@{ sink ->
                try {
                    for (i in 1..1_000) {
                        if (!sink(chunk(4))) {
                            refusedAt.set(i)
                            return@produce
                        }
                    }
                } finally {
                    Thread.sleep(50) // a producer finishing its last sentence
                    producerEnded.set(true)
                }
            },
            consume = { ++consumed < 3 },
        )
        assertTrue("run returned while the producer was still running", producerEnded.get())
        assertTrue(result.cancelled)
        assertEquals(3, consumed)
        // Chunks 1-3 were played; at most two more fit the queue, so the
        // producer is refused at chunk 4, 5 or 6 — never later.
        assertTrue("producer refused at ${refusedAt.get()}", refusedAt.get() in 4..6)
    }

    /**
     * onStop arrives (from a binder thread) while a sentence is still being
     * generated: nothing more is played, the producer is refused at its next
     * chunk, and run returns only after the producer ended.
     */
    @Test
    fun cancelFromAnotherThreadStopsTheRun() {
        val readAhead = ReadAhead(maxAheadSamples = 100)
        val sentenceDone = CountDownLatch(1)
        val producerEnded = AtomicBoolean(false)
        val sinkAnswers = Collections.synchronizedList(ArrayList<Boolean>())
        val stopper = Thread {
            Thread.sleep(100)
            readAhead.cancel()
            Thread.sleep(100)
            sentenceDone.countDown() // the native call returns after the cancel
        }
        stopper.start()
        val result = readAhead.run(
            produce = { sink ->
                sentenceDone.await(5, TimeUnit.SECONDS)
                sinkAnswers += sink(chunk(4))
                producerEnded.set(true)
            },
            consume = { fail("nothing should be played"); true },
        )
        stopper.join(5_000)
        assertTrue(result.cancelled)
        assertTrue(producerEnded.get())
        assertEquals(listOf(false), sinkAnswers.toList())
    }

    @Test
    fun cancelledBeforeRunDoesNotGenerate() {
        val readAhead = ReadAhead(maxAheadSamples = 100)
        readAhead.cancel()
        val result = readAhead.run(produce = { fail("generated after stop") }, consume = { true })
        assertTrue(result.cancelled)
        assertEquals(0L, result.samples)
    }

    @Test
    fun producerFailureIsRethrownOnTheCallingThread() {
        val boom = IllegalStateException("model failed")
        try {
            ReadAhead(maxAheadSamples = 100).run(
                produce = { sink -> sink(chunk(4)); throw boom },
                consume = { true },
            )
            fail("expected the producer's exception")
        } catch (e: IllegalStateException) {
            assertSame(boom, e)
        }
    }

    @Test
    fun listenerFailureStopsTheProducer() {
        val producerEnded = AtomicBoolean(false)
        try {
            ReadAhead(maxAheadSamples = 4).run(
                produce = { sink ->
                    while (sink(chunk(4))) {
                        // keep generating until told to stop
                    }
                    producerEnded.set(true)
                },
                consume = { throw IllegalArgumentException("client gone") },
            )
            fail("expected the listener's exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(producerEnded.get())
        }
    }

    /**
     * The logged speed must not count the time the producer waited for
     * playback — the old "total" did, which made Piper look 5x slower than it
     * is. The producer works ~150 ms but is held up ~200 ms by a listener
     * that needs 300 ms per chunk (queue: one chunk).
     */
    @Test
    fun synthTimeExcludesWaitingForTheListener() {
        val started = System.nanoTime()
        val result = ReadAhead(maxAheadSamples = 4).run(
            produce = { sink ->
                repeat(3) {
                    Thread.sleep(50)
                    sink(chunk(4))
                }
            },
            consume = { Thread.sleep(300); true },
        )
        val wallMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue("wall time $wallMillis", wallMillis >= 850)
        // Counting the wait would give ~350 ms.
        assertTrue("synth time ${result.synthMillis}", result.synthMillis in 130L..280L)
    }

    @Test(expected = IllegalStateException::class)
    fun runsOnlyOnce() {
        val readAhead = ReadAhead(maxAheadSamples = 10)
        readAhead.run(produce = { }, consume = { true })
        readAhead.run(produce = { }, consume = { true })
    }
}
