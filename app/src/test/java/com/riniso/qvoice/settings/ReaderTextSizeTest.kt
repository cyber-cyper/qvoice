package com.riniso.qvoice.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** ReaderTextSize: any stored value lands on one of the four steps. */
class ReaderTextSizeTest {

    @Test
    fun eachStepIsItself() {
        for (step in ReaderTextSize.STEPS) assertEquals(step, ReaderTextSize.nearest(step), 0f)
    }

    @Test
    fun otherValuesGoToTheClosestStep() {
        assertEquals(1f, ReaderTextSize.nearest(0.5f), 0f)
        assertEquals(1.25f, ReaderTextSize.nearest(1.2f), 0f)
        assertEquals(1.5f, ReaderTextSize.nearest(1.45f), 0f)
        assertEquals(1.75f, ReaderTextSize.nearest(9f), 0f)
        assertEquals(1f, ReaderTextSize.nearest(Float.NaN), 0f)
        assertEquals(1f, ReaderTextSize.nearest(Float.POSITIVE_INFINITY), 0f)
    }
}
