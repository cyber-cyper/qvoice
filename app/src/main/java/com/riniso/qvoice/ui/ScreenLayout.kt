package com.riniso.qvoice.ui

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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

/**
 * [flag], but only once it has stayed true for [delayMs]: for "preparing the
 * voice" cues, which must not flicker for waits of a few milliseconds (the
 * gap before a paragraph computed ahead) yet show any wait a listener would
 * notice. Core app quality asks for audio within a second of Play, or a cue.
 */
@Composable
internal fun rememberLastingFlag(flag: Boolean, delayMs: Long = PREPARING_CUE_DELAY_MS): Boolean {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(flag) {
        shown = false
        if (flag) {
            delay(delayMs)
            shown = true
        }
    }
    return shown
}

/** Long enough to skip the gap before a paragraph computed ahead, short enough to show any real wait. */
internal const val PREPARING_CUE_DELAY_MS = 400L
