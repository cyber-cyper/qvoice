package com.riniso.qvoice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedBookTest {

    private class MapStore(val saved: MutableMap<String, Float> = HashMap()) : SpeedBook.Store {
        override fun loadSpeeds(): Map<String, Float> = HashMap(saved)
        override fun saveSpeed(voiceId: String, factor: Float) {
            saved[voiceId] = factor
        }
    }

    @Test
    fun firstSampleIsTakenAsIsThenSmoothed() {
        val store = MapStore()
        val book = SpeedBook(store)
        book.record("kitten", audioMs = 5_400, synthMs = 3_600) // 1.5x
        assertEquals(1.5f, book.measured("kitten")!!, 0.001f)
        book.record("kitten", audioMs = 5_000, synthMs = 2_000) // 2.5x
        // 70% old, 30% new: one fast sample doesn't flip the label.
        assertEquals(1.8f, book.measured("kitten")!!, 0.001f)
        assertEquals(1.8f, store.saved["kitten"]!!, 0.001f)
    }

    @Test
    fun shortOrEmptyUtterancesAreIgnored() {
        val book = SpeedBook(MapStore())
        book.record("v", audioMs = 376, synthMs = 700) // "Hello!": mostly per-call cost
        book.record("v", audioMs = 0, synthMs = 4_366) // stopped before any audio
        book.record("v", audioMs = 5_000, synthMs = 0) // nothing measured
        assertNull(book.measured("v"))
        book.record("v", audioMs = SpeedBook.MIN_AUDIO_MS, synthMs = 3_000)
        assertEquals(0.5f, book.measured("v")!!, 0.001f)
    }

    @Test
    fun measurementsSurviveARestartAndBadValuesDont() {
        val store = MapStore(mutableMapOf("kokoro" to 0.25f, "broken" to Float.NaN, "zero" to 0f))
        val book = SpeedBook(store)
        assertEquals(0.25f, book.measured("kokoro")!!, 0f)
        assertNull(book.measured("broken"))
        assertNull(book.measured("zero"))
    }

    @Test
    fun estimatePrefersMeasuredThenPredicts() {
        val measured = mapOf("builtin" to 1.5f, "piper" to 2.8f)
        val hints = mapOf("piper" to 1.8f, "kokoro" to 0.17f)
        fun of(id: String, m: Map<String, Float> = measured) = SpeedEstimate.of(id, "builtin", { m[it] }, { hints[it] })

        // Measured here: the real number, marked measured.
        assertEquals(SpeedEstimate(2.8f, measured = true), of("piper"))
        // Not yet: built-in speed x the catalogue's relative hint (Galaxy M31: ~0.26x).
        val kokoro = of("kokoro")!!
        assertFalse(kokoro.measured)
        assertEquals(0.255f, kokoro.factor, 0.001f)
        // Nothing to go on: no guess.
        assertNull(of("supertonic"))
        assertNull(of("kokoro", m = emptyMap()))
    }

    @Test
    fun tiers() {
        assertEquals(SpeedTier.FAST, SpeedEstimate(2.8f, true).tier)
        assertEquals(SpeedTier.FAST, SpeedEstimate(SpeedEstimate.FAST_AT, true).tier)
        assertEquals(SpeedTier.KEEPS_UP, SpeedEstimate(1.3f, true).tier)
        assertEquals(SpeedTier.SLOW, SpeedEstimate(1.0f, true).tier) // no margin: pauses when the phone is busy
        assertEquals(SpeedTier.SLOW, SpeedEstimate(0.25f, false).tier)
        assertTrue(SpeedEstimate(SpeedEstimate.KEEPS_UP_AT, true).keepsUp)
        assertFalse(SpeedEstimate(0.9f, true).keepsUp)
    }
}
