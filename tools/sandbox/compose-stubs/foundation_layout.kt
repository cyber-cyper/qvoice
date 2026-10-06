@file:Suppress("unused", "UNUSED_PARAMETER", "NOTHING_TO_INLINE")
package androidx.compose.foundation.layout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@DslMarker annotation class LayoutScopeMarker

@Immutable object Arrangement {
    @Stable interface Horizontal
    @Stable interface Vertical
    @Stable interface HorizontalOrVertical : Horizontal, Vertical
    val Start: Horizontal get() = TODO()
    val Top: Vertical get() = TODO()
    @Stable fun spacedBy(space: Dp): HorizontalOrVertical = TODO()
    @Stable fun spacedBy(space: Dp, alignment: Alignment.Horizontal): Horizontal = TODO()
    @Stable fun spacedBy(space: Dp, alignment: Alignment.Vertical): Vertical = TODO()
}

@LayoutScopeMarker @Immutable interface RowScope {
    @Stable fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier = TODO()
    @Stable fun Modifier.align(alignment: Alignment.Vertical): Modifier = TODO()
}
@LayoutScopeMarker @Immutable interface ColumnScope {
    @Stable fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier = TODO()
    @Stable fun Modifier.align(alignment: Alignment.Horizontal): Modifier = TODO()
}
@LayoutScopeMarker @Immutable interface BoxScope {
    @Stable fun Modifier.align(alignment: Alignment): Modifier = TODO()
}

@Composable inline fun Row(modifier: Modifier = Modifier, horizontalArrangement: Arrangement.Horizontal = Arrangement.Start, verticalAlignment: Alignment.Vertical = Alignment.Top, content: @Composable RowScope.() -> Unit) {}
@Composable inline fun Column(modifier: Modifier = Modifier, verticalArrangement: Arrangement.Vertical = Arrangement.Top, horizontalAlignment: Alignment.Horizontal = Alignment.Start, content: @Composable ColumnScope.() -> Unit) {}
@Composable inline fun Box(modifier: Modifier = Modifier, contentAlignment: Alignment = Alignment.TopStart, propagateMinConstraints: Boolean = false, content: @Composable BoxScope.() -> Unit) {}
@Composable fun Box(modifier: Modifier) {}
@Stable interface BoxWithConstraintsScope : BoxScope {
    val minWidth: Dp
    val maxWidth: Dp
    val minHeight: Dp
    val maxHeight: Dp
}
@Composable fun BoxWithConstraints(modifier: Modifier = Modifier, contentAlignment: Alignment = Alignment.TopStart, propagateMinConstraints: Boolean = false, content: @Composable BoxWithConstraintsScope.() -> Unit) {}
@Composable fun Spacer(modifier: Modifier) {}

@Stable interface PaddingValues {
    fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp
    fun calculateTopPadding(): Dp
    fun calculateRightPadding(layoutDirection: LayoutDirection): Dp
    fun calculateBottomPadding(): Dp
}
@Stable fun PaddingValues(all: Dp): PaddingValues = TODO()
@Stable fun PaddingValues(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): PaddingValues = TODO()
@Stable fun PaddingValues(start: Dp = 0.dp, top: Dp = 0.dp, end: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues = TODO()
@Stable fun PaddingValues.calculateStartPadding(layoutDirection: LayoutDirection): Dp = TODO()
@Stable fun PaddingValues.calculateEndPadding(layoutDirection: LayoutDirection): Dp = TODO()

interface WindowInsets

@Stable fun Modifier.fillMaxSize(fraction: Float = 1f): Modifier = TODO()
@Stable fun Modifier.fillMaxWidth(fraction: Float = 1f): Modifier = TODO()
@Stable fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier = TODO()
@Stable fun Modifier.height(height: Dp): Modifier = TODO()
@Stable fun Modifier.width(width: Dp): Modifier = TODO()
@Stable fun Modifier.size(size: Dp): Modifier = TODO()
@Stable fun Modifier.size(width: Dp, height: Dp): Modifier = TODO()
@Stable fun Modifier.padding(start: Dp = 0.dp, top: Dp = 0.dp, end: Dp = 0.dp, bottom: Dp = 0.dp): Modifier = TODO()
@Stable fun Modifier.padding(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): Modifier = TODO()
@Stable fun Modifier.padding(all: Dp): Modifier = TODO()
@Stable fun Modifier.padding(paddingValues: PaddingValues): Modifier = TODO()
fun Modifier.imePadding(): Modifier = TODO()
fun Modifier.consumeWindowInsets(paddingValues: PaddingValues): Modifier = TODO()
