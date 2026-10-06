@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.compose.ui.semantics
import androidx.compose.ui.Modifier
@JvmInline value class Role(val value: Int) {
    companion object {
        val Button: Role get() = TODO()
        val Checkbox: Role get() = TODO()
        val RadioButton: Role get() = TODO()
    }
}
class SemanticsPropertyKey<T>
interface SemanticsPropertyReceiver { operator fun <T> set(key: SemanticsPropertyKey<T>, value: T) }
var SemanticsPropertyReceiver.stateDescription: String
    get() = TODO()
    set(value) {}
var SemanticsPropertyReceiver.contentDescription: String
    get() = TODO()
    set(value) {}
fun SemanticsPropertyReceiver.heading() {}
fun Modifier.semantics(mergeDescendants: Boolean = false, properties: (SemanticsPropertyReceiver.() -> Unit)): Modifier = TODO()
fun Modifier.clearAndSetSemantics(properties: (SemanticsPropertyReceiver.() -> Unit)): Modifier = TODO()
