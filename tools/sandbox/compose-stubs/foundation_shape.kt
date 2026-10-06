@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.compose.foundation.shape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
class RoundedCornerShape private constructor() : Shape
fun RoundedCornerShape(size: Dp): RoundedCornerShape = TODO()
fun RoundedCornerShape(size: Float): RoundedCornerShape = TODO()
fun RoundedCornerShape(percent: Int): RoundedCornerShape = TODO()
fun RoundedCornerShape(topStart: Dp = 0.dp, topEnd: Dp = 0.dp, bottomEnd: Dp = 0.dp, bottomStart: Dp = 0.dp): RoundedCornerShape = TODO()
fun RoundedCornerShape(topStart: Float = 0.0f, topEnd: Float = 0.0f, bottomEnd: Float = 0.0f, bottomStart: Float = 0.0f): RoundedCornerShape = TODO()
fun RoundedCornerShape(topStartPercent: Int = 0, topEndPercent: Int = 0, bottomEndPercent: Int = 0, bottomStartPercent: Int = 0): RoundedCornerShape = TODO()
val CircleShape: RoundedCornerShape get() = TODO()
