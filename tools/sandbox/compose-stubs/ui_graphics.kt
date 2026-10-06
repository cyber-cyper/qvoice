@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.compose.ui.graphics
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset

@Immutable @JvmInline value class Color(val value: ULong) {
    companion object {
        val Black: Color get() = TODO()
        val White: Color get() = TODO()
        val Transparent: Color get() = TODO()
        val Unspecified: Color get() = TODO()
    }
}
@Stable fun Color(color: Long): Color = TODO()
@Stable fun Color(color: Int): Color = TODO()
@Stable fun Color(red: Int, green: Int, blue: Int, alpha: Int = 0xFF): Color = TODO()

@JvmInline value class TileMode(val value: Int) { companion object { val Clamp: TileMode get() = TODO() } }

@Immutable abstract class Brush {
    companion object {
        @Stable fun linearGradient(vararg colorStops: Pair<Float, Color>, start: Offset = Offset.Zero, end: Offset = Offset.Infinite, tileMode: TileMode = TileMode.Clamp): Brush = TODO()
        @Stable fun linearGradient(colors: List<Color>, start: Offset = Offset.Zero, end: Offset = Offset.Infinite, tileMode: TileMode = TileMode.Clamp): Brush = TODO()
    }
}
@Stable interface Shape
val RectangleShape: Shape get() = TODO()
class ColorFilter
interface ImageBitmap
fun interface ColorProducer { operator fun invoke(): Color }
@JvmInline value class FilterQuality(val value: Int)
@JvmInline value class StrokeCap(val value: Int)
