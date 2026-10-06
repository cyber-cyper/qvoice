@file:Suppress("unused")
package androidx.compose.ui

interface Modifier {
    infix fun then(other: Modifier): Modifier = other
    companion object : Modifier
}

interface Alignment {
    interface Horizontal
    interface Vertical
    companion object {
        val TopStart: Alignment get() = TODO()
        val Center: Alignment get() = TODO()
        val Top: Vertical get() = TODO()
        val CenterVertically: Vertical get() = TODO()
        val Bottom: Vertical get() = TODO()
        val Start: Horizontal get() = TODO()
        val CenterHorizontally: Horizontal get() = TODO()
        val End: Horizontal get() = TODO()
    }
}
fun Modifier.keepScreenOn(): Modifier = TODO()
