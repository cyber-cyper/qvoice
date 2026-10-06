@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.compose.runtime.saveable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState

interface Saver<Original, Saveable : Any>
fun <T> autoSaver(): Saver<T, Any> = TODO()

@Deprecated("'rememberSaveable' with a custom 'key' is no longer supported.")
@Composable fun <T : Any> rememberSaveable(vararg inputs: Any?, saver: Saver<T, out Any> = autoSaver(), key: String? = null, init: () -> T): T = TODO()
@Composable fun <T : Any> rememberSaveable(vararg inputs: Any?, init: () -> T): T = TODO()
@Composable fun <T : Any> rememberSaveable(vararg inputs: Any?, saver: Saver<T, out Any>, init: () -> T): T = TODO()
@Composable fun <T> rememberSaveable(vararg inputs: Any?, stateSaver: Saver<T, out Any>, init: () -> MutableState<T>): MutableState<T> = TODO()
@Deprecated("'rememberSaveable' with a custom 'key' is no longer supported.")
@Composable fun <T> rememberSaveable(vararg inputs: Any?, stateSaver: Saver<T, out Any>, key: String? = null, init: () -> MutableState<T>): MutableState<T> = TODO()
