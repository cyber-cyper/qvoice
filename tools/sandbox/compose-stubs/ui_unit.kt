@file:Suppress("unused", "NOTHING_TO_INLINE")
package androidx.compose.ui.unit
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
@Immutable @JvmInline value class Dp(val value: Float) : Comparable<Dp> {
    @Stable inline operator fun plus(other: Dp): Dp = Dp(value + other.value)
    @Stable inline operator fun minus(other: Dp): Dp = Dp(value - other.value)
    @Stable inline operator fun times(other: Float): Dp = Dp(value * other)
    override fun compareTo(other: Dp): Int = value.compareTo(other.value)
}
@Stable inline val Int.dp: Dp get() = Dp(this.toFloat())
@Stable inline val Double.dp: Dp get() = Dp(this.toFloat())
@Stable inline val Float.dp: Dp get() = Dp(this)
@JvmInline value class TextUnit(val packedValue: Long) { companion object { val Unspecified: TextUnit get() = TODO() } }
enum class LayoutDirection { Ltr, Rtl }
@Immutable @JvmInline value class DpOffset(val packedValue: Long)
