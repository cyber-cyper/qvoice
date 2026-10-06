package com.riniso.qvoice.settings

/**
 * The reader's text sizes: four steps on top of the phone's own font size
 * (which already reaches 200% on Android 14+). Steps rather than a slider:
 * each one is a clear difference, TalkBack can name them, and the menu that
 * offers them stays short.
 */
object ReaderTextSize {

    /** Normal, large, larger, largest. */
    val STEPS = listOf(1f, 1.25f, 1.5f, 1.75f)

    /**
     * The step closest to [scale]: a stored value that is damaged, or from a
     * future version with other steps, still lands on one of these.
     */
    fun nearest(scale: Float): Float =
        if (!scale.isFinite()) STEPS.first() else STEPS.minBy { kotlin.math.abs(it - scale) }
}
