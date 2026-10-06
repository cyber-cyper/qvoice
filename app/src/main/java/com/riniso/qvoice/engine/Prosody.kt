package com.riniso.qvoice.engine

import com.riniso.qvoice.engine.sonic.Sonic
import java.util.Locale
import kotlin.math.abs

/**
 * How a requested speech rate and pitch are produced.
 *
 * Android passes both as percentages (100 = normal); its settings offer rates
 * from 0.1x to 6x, and screen-reader users often listen at 2-4x.
 *
 * - The model itself speaks at up to [MAX_MODEL_SPEED] (sherpa-onnx's speed,
 *   the inverse of the length scale): natural prosody, and fewer frames to
 *   make, so faster speech also costs less to generate.
 * - Beyond that, a pitch-preserving time-stretch of the model's output
 *   (Sonic, built for fast TTS listening) does the rest ([tempo]). Measured
 *   on the built-in voice and Piper Kristin with Whisper as the listener
 *   (docs/ARCHITECTURE.md, D-041): the models' own speed control blurs
 *   syllables past ~1.5x and then stops getting faster (asked for 3x, Piper
 *   delivers 2x, Kitten 2.6x); a stretch of their 1.5x output was as clear or
 *   clearer at every rate from 2x to 4x, and reaches the rate asked for.
 * - Slower than [MIN_MODEL_SPEED], Sonic slows down likewise.
 * - [pitch] is Sonic's alone (resampling plus time-stretch; 1 = unchanged).
 */
data class Prosody(val modelSpeed: Float, val tempo: Float, val pitch: Float) {

    /** The model's output has to pass through Sonic (anything but plain speech). */
    val needsStretch: Boolean get() = abs(tempo - 1f) > EPSILON || abs(pitch - 1f) > EPSILON

    /** For the per-utterance log line: "speed 3.00x (model 1.50x, stretch 2.00x), pitch 1.00x". */
    fun describe(): String =
        "speed ${format(modelSpeed * tempo)}x (model ${format(modelSpeed)}x, stretch ${format(tempo)}x), pitch ${format(pitch)}x"

    companion object {
        const val MIN_RATE = 0.25f
        const val MAX_RATE = 6f
        const val MIN_MODEL_SPEED = 0.5f
        const val MAX_MODEL_SPEED = 1.5f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2f
        private const val EPSILON = 0.001f

        val NORMAL = Prosody(1f, 1f, 1f)

        private fun format(x: Float) = String.format(Locale.ROOT, "%.2f", x)

        /** From the framework's percentages; out-of-range or missing values are clamped. */
        fun of(speechRate: Int, pitch: Int): Prosody {
            val rate = (speechRate / 100f).coerceIn(MIN_RATE, MAX_RATE)
            val model = rate.coerceIn(MIN_MODEL_SPEED, MAX_MODEL_SPEED)
            return Prosody(model, rate / model, (pitch / 100f).coerceIn(MIN_PITCH, MAX_PITCH))
        }
    }
}

/**
 * Applies a [Prosody]'s tempo and pitch to streamed speech. One per
 * utterance, used from one thread (the TTS service's playback side).
 *
 * Each chunk is processed whole and flushed. Chunks end at sentence or clause
 * boundaries, where the models leave a short silence, so nothing is held back
 * for the next chunk — which may come seconds later on a slow phone and would
 * otherwise carry the last few milliseconds of a word across the pause.
 */
class TempoStretcher(sampleRate: Int, prosody: Prosody) {

    private val sonic = Sonic(sampleRate, 1).apply {
        speed = prosody.tempo
        pitch = prosody.pitch
    }

    /** [chunk] stretched; about `chunk.size / tempo` samples. */
    fun process(chunk: FloatArray): FloatArray {
        if (chunk.isEmpty()) return chunk
        sonic.writeFloatToStream(chunk, chunk.size)
        sonic.flushStream()
        val available = sonic.samplesAvailable()
        val out = FloatArray(available)
        if (available > 0) sonic.readFloatFromStream(out, available)
        return out
    }
}
