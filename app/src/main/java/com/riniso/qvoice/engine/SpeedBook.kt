package com.riniso.qvoice.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * How fast each voice runs on this phone, learnt from what it actually spoke.
 *
 * Why it exists: speed is what decides whether a voice is usable on a given
 * phone, and it varies more than anyone would guess. On a Galaxy M31 Kokoro
 * generated at 0.25x real time — every sentence followed by seconds of
 * silence — while the built-in voice ran at 1.5x and Piper at 2.8x. Measured
 * speeds let the voice library warn before a download and keep the automatic
 * default voice on one that keeps up (VoiceSelector).
 *
 * A sample is one utterance's generated audio over its generating time (not
 * waiting for playback; see ReadAhead). Utterances under [MIN_AUDIO_MS] are
 * ignored: a lone "OK" is mostly fixed per-call cost and would understate what
 * continuous reading gets. Samples are smoothed ([WEIGHT] for the newest), so
 * one busy moment on the phone doesn't flip a label.
 *
 * Thread-safe: the TTS service records from its synthesis thread, the speed
 * check from a background thread, the UI reads from the main thread.
 */
class SpeedBook(private val store: Store) {

    /** Where measurements persist (a device-only preferences file in the app). */
    interface Store {
        fun loadSpeeds(): Map<String, Float>
        fun saveSpeed(voiceId: String, factor: Float)
    }

    private val speeds = ConcurrentHashMap(store.loadSpeeds().filterValues { it.isFinite() && it > 0f })

    /** Records one utterance of [voiceId]. */
    fun record(voiceId: String, audioMs: Long, synthMs: Long) {
        if (audioMs < MIN_AUDIO_MS || synthMs <= 0L) return
        val sample = audioMs.toFloat() / synthMs
        if (!sample.isFinite() || sample <= 0f) return
        val updated = speeds.merge(voiceId, sample) { old, new -> old * (1 - WEIGHT) + new * WEIGHT } ?: return
        store.saveSpeed(voiceId, updated)
    }

    /** Times faster than real time [voiceId] generated here, or null if it hasn't spoken enough yet. */
    fun measured(voiceId: String): Float? = speeds[voiceId]

    companion object {
        const val MIN_AUDIO_MS = 1_500L
        const val WEIGHT = 0.3f
    }
}

/** How a voice's speed on this phone reads to a listener. */
enum class SpeedTier { FAST, KEEPS_UP, SLOW }

/**
 * A voice's speed on this phone: [factor] times faster than real time,
 * [measured] here or predicted (see [of]).
 */
data class SpeedEstimate(val factor: Float, val measured: Boolean) {

    val tier: SpeedTier
        get() = when {
            factor >= FAST_AT -> SpeedTier.FAST
            factor >= KEEPS_UP_AT -> SpeedTier.KEEPS_UP
            else -> SpeedTier.SLOW
        }

    /** Fast enough to read continuously without pauses (after the first sentence). */
    val keepsUp: Boolean get() = factor >= KEEPS_UP_AT

    companion object {
        /** Clearly ahead: sentences are ready long before they are needed. */
        const val FAST_AT = 1.5f

        /**
         * Generation runs ahead of playback (ReadAhead), so anything faster
         * than real time keeps up in principle; the 10% margin absorbs the
         * phone being busy for a moment.
         */
        const val KEEPS_UP_AT = 1.1f

        /**
         * The measured speed if [voiceId] has spoken on this phone; otherwise
         * the reference voice's measured speed times the catalogue's hint for
         * [voiceId] (its speed relative to that reference, from phone tests);
         * null when neither is known.
         */
        fun of(
            voiceId: String,
            referenceId: String,
            measured: (String) -> Float?,
            hint: (String) -> Float?,
        ): SpeedEstimate? {
            measured(voiceId)?.let { return SpeedEstimate(it, measured = true) }
            val reference = measured(referenceId) ?: return null
            val relative = hint(voiceId) ?: return null
            return SpeedEstimate(reference * relative, measured = false)
        }
    }
}
