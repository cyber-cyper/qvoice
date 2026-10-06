package com.riniso.qvoice.engine

import kotlin.math.roundToInt

/** Float samples in [-1, 1] -> signed 16-bit little-endian PCM, the format Android's TTS pipeline takes. */
object Pcm16 {

    /**
     * Encodes `samples[from until from + count]` into `out[0 until count * 2]`.
     * Out-of-range values are clipped (models occasionally overshoot 1.0 by a
     * few percent); NaN becomes silence instead of a full-scale click.
     */
    fun encode(samples: FloatArray, from: Int, count: Int, out: ByteArray) {
        require(from >= 0 && count >= 0 && from + count <= samples.size) { "range out of bounds" }
        require(out.size >= count * 2) { "output too small" }
        var o = 0
        for (i in from until from + count) {
            val v = samples[i]
            val s = when {
                v.isNaN() -> 0
                v >= 1f -> 32767
                v <= -1f -> -32768
                else -> (v * 32767f).roundToInt()
            }
            out[o] = (s and 0xFF).toByte()
            out[o + 1] = ((s shr 8) and 0xFF).toByte()
            o += 2
        }
    }
}

/**
 * Converts streamed float chunks to PCM and hands them to [sink] in pieces no
 * larger than [maxBufferBytes] (SynthesisCallback.getMaxBufferSize()).
 *
 * The byte buffer is reused between writes: the framework's playback callback
 * copies what it is given, so there is no need to allocate per chunk.
 *
 * @param sink returns false when the consumer refuses more audio (the client
 *   stopped); [write] then returns false so synthesis can stop too.
 */
class PcmStreamer(maxBufferBytes: Int, private val sink: (ByteArray, Int, Int) -> Boolean) {

    // Even length (whole 16-bit samples) and never zero.
    private val buffer = ByteArray(maxOf(2, maxBufferBytes and 1.inv()))

    var bytesWritten: Long = 0
        private set

    fun write(samples: FloatArray): Boolean {
        val perChunk = buffer.size / 2
        var i = 0
        while (i < samples.size) {
            val n = minOf(perChunk, samples.size - i)
            Pcm16.encode(samples, i, n, buffer)
            if (!sink(buffer, 0, n * 2)) return false
            bytesWritten += n * 2L
            i += n
        }
        return true
    }
}
