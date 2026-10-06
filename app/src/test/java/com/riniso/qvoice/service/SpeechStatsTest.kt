package com.riniso.qvoice.service

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechStatsTest {

    private fun stats(synthMs: Long, audioMs: Long) =
        SpeechStats("app", "voice", firstAudioMs = 300, synthMs = synthMs, audioMs = audioMs, threads = 4, stopped = false)

    @Test
    fun speedIsAudioOverGeneratingTime() {
        // Kristin on the Galaxy M31: ~0.95 s to generate 4.9 s of audio.
        assertEquals(5.16f, stats(synthMs = 950, audioMs = 4_900).speedFactor, 0.01f)
        assertEquals("5.2", SpeechStats.formatFactor(stats(950, 4_900).speedFactor))
        // Kitten nano int8 there: slower than real time.
        assertEquals("0.8", SpeechStats.formatFactor(stats(5_430, 4_470).speedFactor))
    }

    @Test
    fun unknownSpeedIsZero() {
        assertEquals(0f, stats(synthMs = 0, audioMs = 1_000).speedFactor, 0f)
        assertEquals(0f, stats(synthMs = 700, audioMs = 0).speedFactor, 0f) // stopped before any audio
    }
}
