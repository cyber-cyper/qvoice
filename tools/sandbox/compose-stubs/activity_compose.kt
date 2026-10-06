@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.activity.compose
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
@Composable fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {}
fun ComponentActivity.setContent(parent: CompositionContext? = null, content: @Composable () -> Unit) {}
