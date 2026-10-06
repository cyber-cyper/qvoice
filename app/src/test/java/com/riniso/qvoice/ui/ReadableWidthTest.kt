package com.riniso.qvoice.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** ReadableWidth: phones keep their margins; wide screens centre the content; side bars are cleared. */
class ReadableWidthTest {

    private fun pad(available: Float, start: Float = 0f, end: Float = 0f) = ReadableWidth.sidePadding(available, start, end)

    @Test
    fun phonesInPortraitKeepTheirMargins() {
        assertEquals(16f to 16f, pad(411f)) // Galaxy M31
        assertEquals(16f to 16f, pad(672f)) // the widest that still fits 640 + 2 × 16
    }

    @Test
    fun wideScreensCentreTheContentAtItsReadableWidth() {
        val (start, end) = pad(1280f)
        assertEquals(320f to 320f, start to end)
        assertEquals(ReadableWidth.MAX_DP, 1280f - start - end)
    }

    @Test
    fun aNavigationBarAtTheSideIsClearedAsWell() {
        // Landscape phone, 3-button navigation on the right.
        val (start, end) = pad(891f, start = 0f, end = 48f)
        assertEquals(ReadableWidth.MAX_DP, 891f - start - end, 0.001f)
        assertEquals(start + 48f, end, 0.001f)
        // Narrow and with a bar: the margin never drops below 16 dp beside it.
        assertEquals(16f to 64f, pad(500f, start = 0f, end = 48f))
    }
}
