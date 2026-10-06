package com.riniso.qvoice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Prosody (how rate and pitch are split) and TempoStretcher (Sonic, streamed). */
class ProsodyTest {

    private fun rateOf(p: Prosody) = p.modelSpeed * p.tempo

    @Test
    fun normalSpeechSkipsTheStretcher() {
        val p = Prosody.of(100, 100)
        assertEquals(Prosody.NORMAL, p)
        assertFalse(p.needsStretch)
    }

    @Test
    fun moderateRatesAreTheModelsOwn() {
        val fast = Prosody.of(150, 100)
        assertEquals(1.5f, fast.modelSpeed, 1e-6f)
        assertFalse(fast.needsStretch)
        val slow = Prosody.of(60, 100)
        assertEquals(0.6f, slow.modelSpeed, 1e-6f)
        assertFalse(slow.needsStretch)
    }

    @Test
    fun fasterRatesStretchTheModelsFastestClearSpeed() {
        val p = Prosody.of(300, 100)
        assertEquals(Prosody.MAX_MODEL_SPEED, p.modelSpeed, 1e-6f)
        assertEquals(3f, rateOf(p), 1e-4f)
        assertTrue(p.needsStretch)
    }

    @Test
    fun verySlowRatesAreStretchedToo() {
        val p = Prosody.of(30, 100)
        assertEquals(Prosody.MIN_MODEL_SPEED, p.modelSpeed, 1e-6f)
        assertEquals(0.3f, rateOf(p), 1e-4f)
        assertTrue(p.needsStretch)
    }

    @Test
    fun outOfRangeRequestsAreClamped() {
        assertEquals(Prosody.MAX_RATE, rateOf(Prosody.of(10_000, 100)), 1e-4f)
        assertEquals(Prosody.MIN_RATE, rateOf(Prosody.of(0, 100)), 1e-4f)
        assertEquals(Prosody.MIN_RATE, rateOf(Prosody.of(-50, 100)), 1e-4f)
        assertEquals(Prosody.MAX_PITCH, Prosody.of(100, 1_000).pitch, 1e-6f)
        assertEquals(Prosody.MIN_PITCH, Prosody.of(100, 0).pitch, 1e-6f)
    }

    @Test
    fun aPitchChangeAloneUsesTheStretcher() {
        val p = Prosody.of(100, 130)
        assertEquals(1f, p.modelSpeed, 1e-6f)
        assertEquals(1f, p.tempo, 1e-6f)
        assertEquals(1.3f, p.pitch, 1e-6f)
        assertTrue(p.needsStretch)
    }

    // ---- TempoStretcher ----

    private val rate = 24_000

    /** Speech-like: a 140 Hz voice with harmonics, its loudness rising and falling like syllables. */
    private fun voiced(seconds: Float, f0: Double = 140.0): FloatArray =
        FloatArray((seconds * rate).toInt()) { i ->
            val t = i.toDouble() / rate
            var v = 0.0
            for (k in 1..8) v += sin(2 * PI * f0 * k * t) / k
            (0.25 * v * (0.6 + 0.4 * sin(2 * PI * 4 * t))).toFloat()
        }

    /** Fundamental frequency of the middle of [x], by autocorrelation (70-400 Hz). */
    private fun f0Of(x: FloatArray): Double {
        val from = x.size / 4
        val n = x.size / 2
        var bestLag = 0
        var best = Double.NEGATIVE_INFINITY
        for (lag in rate / 400..rate / 70) {
            var sum = 0.0
            for (i in from until from + n - lag) sum += x[i].toDouble() * x[i + lag]
            if (sum > best) {
                best = sum
                bestLag = lag
            }
        }
        return rate.toDouble() / bestLag
    }

    @Test
    fun tempoShortensAndKeepsThePitch() {
        val input = voiced(2f)
        val out = TempoStretcher(rate, Prosody(1.5f, 2f, 1f)).process(input)
        assertEquals(input.size / 2.0, out.size.toDouble(), input.size * 0.03)
        assertEquals(140.0, f0Of(out), 5.0)
    }

    @Test
    fun slowingDownLengthensAndKeepsThePitch() {
        val input = voiced(1f)
        val out = TempoStretcher(rate, Prosody(0.5f, 0.5f, 1f)).process(input)
        assertEquals(input.size * 2.0, out.size.toDouble(), input.size * 0.06)
        assertEquals(140.0, f0Of(out), 5.0)
    }

    @Test
    fun pitchMovesThePitchNotTheLength() {
        val input = voiced(2f)
        val out = TempoStretcher(rate, Prosody(1f, 1f, 1.5f)).process(input)
        assertEquals(input.size.toDouble(), out.size.toDouble(), input.size * 0.03)
        assertEquals(210.0, f0Of(out), 8.0)
    }

    /**
     * Chunks are flushed: all of a chunk comes out with it, nothing waits for
     * the next one (which on a slow phone may come seconds later).
     */
    @Test
    fun everyChunkComesOutWhole() {
        val stretcher = TempoStretcher(rate, Prosody(1.5f, 2f, 1f))
        val first = stretcher.process(voiced(1f))
        val second = stretcher.process(voiced(0.5f))
        // To within a few ms: unflushed, Sonic keeps back ~15 ms of every chunk.
        assertEquals(rate / 2.0, first.size.toDouble(), 60.0)
        assertEquals(rate / 4.0, second.size.toDouble(), 60.0)
        assertEquals(0, stretcher.process(FloatArray(0)).size)
    }

    /**
     * Neural voices peak a few percent above 1.0. Upstream Sonic cast such
     * samples straight to 16 bits, which wraps a +1.2 peak round to about
     * -0.8: a full-scale click. The vendored copy clips first.
     */
    @Test
    fun overshootIsClippedNotWrapped() {
        val input = FloatArray(rate / 10) { i -> (1.2 * sin(2 * PI * 200 * i / rate)).toFloat() }
        val out = TempoStretcher(rate, Prosody(1f, 1f, 1f)).process(input)
        assertEquals(input.size, out.size)
        for (i in input.indices) assertEquals("sample $i", input[i].coerceIn(-1f, 1f), out[i], 1e-3f)
    }
}
