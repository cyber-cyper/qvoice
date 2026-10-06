package com.riniso.qvoice.ui

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The content padding of a screen's scrolling content, inside the
 * BoxWithConstraints that measures the screen: the Scaffold's bars above
 * and below plus [top] and [bottom], and at the sides [ReadableWidth]'s
 * margins, system bars included. (Before, the sides were a fixed 16 dp, so
 * in landscape a navigation bar on the side could cover the content's edge.)
 * The list itself stays full width, so it scrolls from anywhere.
 */
@Composable
internal fun BoxWithConstraintsScope.readablePadding(scaffold: PaddingValues, top: Dp, bottom: Dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    val (start, end) = ReadableWidth.sidePadding(
        maxWidth.value,
        scaffold.calculateStartPadding(direction).value,
        scaffold.calculateEndPadding(direction).value,
    )
    return PaddingValues(
        start = start.dp,
        top = scaffold.calculateTopPadding() + top,
        end = end.dp,
        bottom = scaffold.calculateBottomPadding() + bottom,
    )
}
