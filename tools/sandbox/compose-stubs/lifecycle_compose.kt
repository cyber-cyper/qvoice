@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.lifecycle.compose
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

val LocalLifecycleOwner: ProvidableCompositionLocal<LifecycleOwner> get() = TODO()

@Composable fun LifecycleEventEffect(event: Lifecycle.Event, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current, onEvent: () -> Unit) {}

interface LifecyclePauseOrDisposeEffectResult
class LifecycleResumePauseEffectScope(override val lifecycle: Lifecycle) : LifecycleOwner {
    inline fun onPauseOrDispose(crossinline onPauseOrDisposeEffect: LifecycleOwner.() -> Unit): LifecyclePauseOrDisposeEffectResult = TODO()
}
@Deprecated("LifecycleResumeEffect must provide one or more 'key' parameters", level = DeprecationLevel.ERROR)
@Composable fun LifecycleResumeEffect(lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current, effects: LifecycleResumePauseEffectScope.() -> LifecyclePauseOrDisposeEffectResult) {}
@Composable fun LifecycleResumeEffect(key1: Any?, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current, effects: LifecycleResumePauseEffectScope.() -> LifecyclePauseOrDisposeEffectResult) {}
@Composable fun LifecycleResumeEffect(key1: Any?, key2: Any?, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current, effects: LifecycleResumePauseEffectScope.() -> LifecyclePauseOrDisposeEffectResult) {}
@Composable fun LifecycleResumeEffect(key1: Any?, key2: Any?, key3: Any?, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current, effects: LifecycleResumePauseEffectScope.() -> LifecyclePauseOrDisposeEffectResult) {}
@Composable fun LifecycleResumeEffect(vararg keys: Any?, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current, effects: LifecycleResumePauseEffectScope.() -> LifecyclePauseOrDisposeEffectResult) {}

@Composable fun <T> StateFlow<T>.collectAsStateWithLifecycle(lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current, minActiveState: Lifecycle.State = Lifecycle.State.STARTED, context: CoroutineContext = EmptyCoroutineContext): State<T> = TODO()
@Composable fun <T> StateFlow<T>.collectAsStateWithLifecycle(lifecycle: Lifecycle, minActiveState: Lifecycle.State = Lifecycle.State.STARTED, context: CoroutineContext = EmptyCoroutineContext): State<T> = TODO()
@Composable fun <T> Flow<T>.collectAsStateWithLifecycle(initialValue: T, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current, minActiveState: Lifecycle.State = Lifecycle.State.STARTED, context: CoroutineContext = EmptyCoroutineContext): State<T> = TODO()
@Composable fun <T> Flow<T>.collectAsStateWithLifecycle(initialValue: T, lifecycle: Lifecycle, minActiveState: Lifecycle.State = Lifecycle.State.STARTED, context: CoroutineContext = EmptyCoroutineContext): State<T> = TODO()
