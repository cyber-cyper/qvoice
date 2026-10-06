package com.riniso.qvoice.engine

import com.riniso.qvoice.voices.InstalledVoice
import java.util.concurrent.locks.ReentrantLock

/**
 * A loaded speech model. Wraps sherpa-onnx's OfflineTts behind an interface so
 * the caching and locking in [EngineHost] can be unit-tested with fakes.
 */
interface SpeechModel {
    val sampleRate: Int
    val numSpeakers: Int

    /** CPU threads the model runs with, for the log and the speed line (0 = not known). */
    val threads: Int get() = 0

    /**
     * Synthesizes [text], handing audio to [onChunk] as it is produced (one
     * sentence per chunk). [onChunk] returns false to stop early.
     *
     * @param language ISO-639-1 code of the text, for models that take the
     *   language per request (Supertonic); ignored by the others, which fix it
     *   when loaded (see EngineHost variants).
     * @param opening whether someone waits for this text's first sound: then
     *   its first sentences are cut short for fast first audio (TextChunker's
     *   opening). False for a text queued while another plays (the reader's
     *   next sentence): it is ready in time anyway, and the cut would only
     *   add a pause inside a sentence.
     */
    fun generate(text: String, sid: Int, speed: Float, language: String?, opening: Boolean = true, onChunk: (FloatArray) -> Boolean)

    /** Frees native memory. Never called while [generate] is running (see [EngineHost]). */
    fun release()
}

class EngineLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Owns the loaded models: loads on demand, keeps the most recently used ones
 * in memory within two limits — at most [maxLoaded] models and at most
 * [maxBytes] of model files (estimated by [sizeOf]) — and releases the rest.
 *
 * The byte budget matters since slice 2: Kokoro is ~360 MB and sherpa-onnx
 * fixes its language per instance, so English and Hindi are two instances.
 * Holding both would put a background service at ~750 MB — first in line for
 * Android's low-memory killer, which users see as "the voice app crashed".
 * Room is made *before* loading, so the peak is budget + one model, not
 * budget + two. A model bigger than the whole budget still loads, alone.
 *
 * Concurrency rule — the one thing this class exists to guarantee: **a model
 * is never released while it is generating.** Releasing sherpa-onnx's native
 * object mid-generation is a use-after-free (SIGSEGV, the whole app dies),
 * which is how multi-engine apps crash when a warm-up or a memory trim races a
 * synthesis. So one lock covers load, use and release:
 *
 * - [withModel] holds it for the whole synthesis.
 * - [warmUp] and [releaseIdle] only *try* the lock and skip when a synthesis
 *   is running — a warm-up that waits would queue the user's next sentence
 *   behind a model they may not need; a trim that waits can do its job later.
 *
 * Model loading happens under the lock too, so two callers never load the
 * same (hundreds of MB) model twice.
 */
class EngineHost(
    private val loader: ModelLoader,
    private val maxLoaded: Int = DEFAULT_MAX_LOADED,
    private val maxBytes: Long = Long.MAX_VALUE,
    private val sizeOf: (InstalledVoice) -> Long = { 0L },
) {
    fun interface ModelLoader {
        /** @param variant family-specific load variant (Kokoro: text language); null for most voices. */
        fun load(voice: InstalledVoice, variant: String?): SpeechModel

        /**
         * The settings a load uses (today: the thread count). A cached model
         * loaded under another signature is released and loaded again the
         * next time it is needed — so a changed setting applies from the next
         * sentence on, and never to a model in the middle of speaking.
         */
        val signature: String get() = ""
    }

    private class Entry(val voiceId: String, val model: SpeechModel, val bytes: Long, val signature: String)

    init {
        require(maxLoaded >= 1) { "maxLoaded must be at least 1" }
        require(maxBytes > 0) { "maxBytes must be positive" }
    }

    private val lock = ReentrantLock()

    // Access-ordered: iteration starts at the least recently used entry.
    private val loaded = LinkedHashMap<String, Entry>(8, 0.75f, true)

    // Sum of Entry.bytes over [loaded]; guarded by [lock].
    private var loadedBytes = 0L

    /**
     * Runs [block] with the model for [voice] loaded, holding the engine lock
     * until [block] returns. Loads the model first if needed (may take
     * seconds); throws [EngineLoadException] if it cannot be loaded.
     */
    fun <T> withModel(voice: InstalledVoice, variant: String?, block: (SpeechModel) -> T): T {
        lock.lock()
        try {
            return block(obtainLocked(voice, variant))
        } finally {
            lock.unlock()
        }
    }

    /**
     * Loads [voice] ahead of time if nothing is synthesizing right now.
     * @return true if the model is loaded afterwards.
     */
    fun warmUp(voice: InstalledVoice, variant: String?): Boolean {
        if (!lock.tryLock()) return false
        try {
            obtainLocked(voice, variant)
            return true
        } catch (e: Exception) {
            return false
        } finally {
            lock.unlock()
        }
    }

    /** Test observation helper. */
    internal fun isLoaded(voiceId: String, variant: String?): Boolean {
        lock.lock()
        try {
            return loaded.containsKey(keyOf(voiceId, variant))
        } finally {
            lock.unlock()
        }
    }

    /**
     * Releases every loaded model unless a synthesis is running.
     * @return true if memory was released (or nothing was loaded).
     */
    fun releaseIdle(): Boolean {
        if (!lock.tryLock()) return false
        try {
            releaseAllLocked()
            return true
        } finally {
            lock.unlock()
        }
    }

    /** Releases all loaded variants of one voice (e.g. before its files are deleted). */
    fun release(voiceId: String) {
        lock.lock()
        try {
            val iterator = loaded.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next().value
                if (entry.voiceId == voiceId) {
                    iterator.remove()
                    loadedBytes -= entry.bytes
                    runCatching { entry.model.release() }
                }
            }
        } finally {
            lock.unlock()
        }
    }

    private fun obtainLocked(voice: InstalledVoice, variant: String?): SpeechModel {
        val key = keyOf(voice.id, variant)
        val signature = loader.signature
        loaded[key]?.let { entry ->
            if (entry.signature == signature) return entry.model
            // Loaded with other settings. Safe to release: we hold the lock,
            // so nothing is generating with it.
            loaded.remove(key)
            loadedBytes -= entry.bytes
            runCatching { entry.model.release() }
        }
        val bytes = runCatching { sizeOf(voice) }.getOrDefault(0L).coerceAtLeast(0L)
        makeRoomLocked(bytes)
        val model = try {
            loader.load(voice, variant)
        } catch (e: EngineLoadException) {
            throw e
        } catch (t: Throwable) {
            throw EngineLoadException("could not load ${voice.id}: ${t.message ?: t.javaClass.simpleName}", t)
        }
        loaded[key] = Entry(voice.id, model, bytes, signature)
        loadedBytes += bytes
        return model
    }

    /**
     * Drops least-recently-used models until one more of [incomingBytes]
     * fits both limits (or nothing is left). Safe only because it runs under
     * [lock]: no other thread can be inside [withModel] with one of these
     * models. If the load that follows fails, the evicted models simply load
     * again when next needed.
     */
    private fun makeRoomLocked(incomingBytes: Long) {
        val iterator = loaded.entries.iterator()
        while (iterator.hasNext() && (loaded.size >= maxLoaded || loadedBytes + incomingBytes > maxBytes)) {
            val entry = iterator.next().value
            iterator.remove()
            loadedBytes -= entry.bytes
            runCatching { entry.model.release() }
        }
    }

    private fun releaseAllLocked() {
        for (entry in loaded.values) runCatching { entry.model.release() }
        loaded.clear()
        loadedBytes = 0L
    }

    companion object {
        /**
         * Two models: the voice in use plus one more, so switching back and
         * forth (an English and a Hindi voice, say) doesn't reload each time
         * — as long as both fit the byte budget.
         */
        const val DEFAULT_MAX_LOADED = 2

        private const val MIB = 1024L * 1024L

        /**
         * Byte budget for loaded models: a sixth of the phone's RAM, kept
         * between 256 and 768 MiB. On a 4 GB phone that is ~680 MiB: Kokoro
         * (~370) plus Supertonic (~145) stay loaded together, so switching
         * between an English and a Hindi voice doesn't reload each time, but
         * two Kokoro languages (~740) don't. Loaded models are also dropped
         * whenever Android reports memory pressure
         * (QVoiceTtsService.onTrimMemory), so this is a ceiling, not a
         * target. Tune from field reports.
         */
        fun budgetFor(totalRamBytes: Long): Long = (totalRamBytes / 6).coerceIn(256 * MIB, 768 * MIB)

        internal fun keyOf(voiceId: String, variant: String?): String =
            if (variant.isNullOrEmpty()) voiceId else "$voiceId|$variant"
    }
}
