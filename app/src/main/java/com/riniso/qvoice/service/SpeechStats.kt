package com.riniso.qvoice.service

import java.util.Locale

/**
 * What one utterance cost, measured by the engine service: the numbers of
 * its log line, kept for the home screen (same process) to show.
 *
 * @property synthMs time spent generating, not counting waits for playback
 *   (see ReadAhead) — so [speedFactor] is the voice's real speed on this phone.
 * @property audioMs audio generated (more than was played if stopped early).
 */
data class SpeechStats(
    val caller: String,
    val voiceName: String,
    val firstAudioMs: Long,
    val synthMs: Long,
    val audioMs: Long,
    val threads: Int,
    val stopped: Boolean,
) {
    /** How many times faster than real time the voice generated; 0 when unknown. */
    val speedFactor: Float
        get() = if (synthMs > 0 && audioMs > 0) audioMs.toFloat() / synthMs else 0f

    companion object {
        /** 2.3456 -> "2.3", 0.84 -> "0.8"; one decimal is all a listener can tell apart. */
        fun formatFactor(factor: Float): String = String.format(Locale.ROOT, "%.1f", factor)
    }
}
