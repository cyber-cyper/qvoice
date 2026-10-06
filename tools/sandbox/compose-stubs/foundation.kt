@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.compose.foundation
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role

@Composable fun Image(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center, contentScale: ContentScale = ContentScale.Fit, alpha: Float = 1f, colorFilter: ColorFilter? = null) {}
@Composable fun Image(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center, contentScale: ContentScale = ContentScale.Fit, alpha: Float = 1f, colorFilter: ColorFilter? = null) {}
@Composable fun Image(bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center, contentScale: ContentScale = ContentScale.Fit, alpha: Float = 1f, colorFilter: ColorFilter? = null, filterQuality: FilterQuality = TODO()) {}

fun Modifier.background(color: Color, shape: Shape = RectangleShape): Modifier = TODO()
fun Modifier.background(brush: Brush, shape: Shape = RectangleShape, alpha: Float = 1.0f): Modifier = TODO()

fun Modifier.clickable(enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null, interactionSource: MutableInteractionSource? = null, onClick: () -> Unit): Modifier = TODO()
fun Modifier.clickable(interactionSource: MutableInteractionSource?, indication: Indication?, enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null, onClick: () -> Unit): Modifier = TODO()

@Composable @ReadOnlyComposable fun isSystemInDarkTheme(): Boolean = TODO()

class ScrollState
@Composable fun rememberScrollState(initial: Int = 0): ScrollState = TODO()
fun Modifier.verticalScroll(state: ScrollState, enabled: Boolean = true, flingBehavior: FlingBehavior? = null, reverseScrolling: Boolean = false): Modifier = TODO()
fun Modifier.verticalScroll(state: ScrollState, overscrollEffect: OverscrollEffect?, enabled: Boolean = true, flingBehavior: FlingBehavior? = null, reverseScrolling: Boolean = false): Modifier = TODO()

class BorderStroke
interface Indication
interface OverscrollEffect
