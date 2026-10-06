@file:Suppress("unused", "UNUSED_PARAMETER", "NOTHING_TO_INLINE")
package androidx.compose.runtime

import kotlin.reflect.KProperty
import kotlinx.coroutines.CoroutineScope

@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER, AnnotationTarget.PROPERTY_GETTER)
annotation class Composable

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class ReadOnlyComposable

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY)
annotation class Stable

@Target(AnnotationTarget.CLASS)
annotation class Immutable

@Target(AnnotationTarget.TYPE)
annotation class DisallowComposableCalls

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class NonRestartableComposable

@Stable interface State<out T> { val value: T }
@Stable interface MutableState<T> : State<T> { override var value: T }

inline operator fun <T> State<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value
inline operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: KProperty<*>, value: T) { this.value = value }

interface SnapshotMutationPolicy<T>
fun <T> structuralEqualityPolicy(): SnapshotMutationPolicy<T> = TODO()
fun <T> mutableStateOf(value: T, policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy()): MutableState<T> = TODO()
@Stable interface IntState : State<Int> { override val value: Int; val intValue: Int }
@Stable interface MutableIntState : IntState, MutableState<Int> { override var value: Int; override var intValue: Int }
fun mutableIntStateOf(value: Int): MutableIntState = TODO()
inline operator fun IntState.getValue(thisObj: Any?, property: KProperty<*>): Int = intValue
inline operator fun MutableIntState.setValue(thisObj: Any?, property: KProperty<*>, value: Int) { intValue = value }

@Composable inline fun <T> remember(crossinline calculation: @DisallowComposableCalls () -> T): T = calculation()
@Composable inline fun <T> remember(key1: Any?, crossinline calculation: @DisallowComposableCalls () -> T): T = calculation()
@Composable inline fun <T> remember(key1: Any?, key2: Any?, crossinline calculation: @DisallowComposableCalls () -> T): T = calculation()
@Composable inline fun <T> remember(key1: Any?, key2: Any?, key3: Any?, crossinline calculation: @DisallowComposableCalls () -> T): T = calculation()
@Composable inline fun <T> remember(vararg keys: Any?, crossinline calculation: @DisallowComposableCalls () -> T): T = calculation()

@Composable @NonRestartableComposable fun LaunchedEffect(key1: Any?, block: suspend CoroutineScope.() -> Unit) {}
@Composable @NonRestartableComposable fun LaunchedEffect(key1: Any?, key2: Any?, block: suspend CoroutineScope.() -> Unit) {}
@Composable @NonRestartableComposable fun LaunchedEffect(key1: Any?, key2: Any?, key3: Any?, block: suspend CoroutineScope.() -> Unit) {}
@Composable @NonRestartableComposable fun LaunchedEffect(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {}

abstract class CompositionContext

@Stable
abstract class CompositionLocal<T> {
    val current: T
        @ReadOnlyComposable @Composable get() = TODO()
}
abstract class ProvidableCompositionLocal<T> : CompositionLocal<T>()
