package com.riniso.qvoice.ui

/**
 * How wide a screen's content may get: about 70 characters of body text per
 * line (comfortable to read, as Material advises for long text), and cards
 * and lists that don't stretch across a tablet, a foldable or a phone in
 * landscape. Phones in portrait are unaffected: up to 672 dp wide (every
 * phone), the content keeps its 16 dp margins.
 *
 * Plain Kotlin, so it is unit-tested; ScreenLayout.kt applies it.
 */
object ReadableWidth {
    const val MAX_DP = 640f
    const val MIN_MARGIN_DP = 16f

    /**
     * Start and end padding, in dp, for content [availableDp] wide whose
     * sides have [insetStartDp] and [insetEndDp] of system bars (a navigation
     * bar or a camera cut-out in landscape): the bars, plus a margin of at
     * least [MIN_MARGIN_DP] that grows to centre the content once it would
     * be wider than [MAX_DP].
     */
    fun sidePadding(availableDp: Float, insetStartDp: Float, insetEndDp: Float): Pair<Float, Float> {
        val inner = availableDp - insetStartDp - insetEndDp
        val margin = maxOf(MIN_MARGIN_DP, (inner - MAX_DP) / 2)
        return (insetStartDp + margin) to (insetEndDp + margin)
    }
}
