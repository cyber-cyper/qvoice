@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.lifecycle.viewmodel.compose
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
object LocalViewModelStoreOwner { val current: ViewModelStoreOwner? @Composable get() = TODO() }
@Composable inline fun <reified VM : ViewModel> viewModel(viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" }, key: String? = null, factory: ViewModelProvider.Factory? = null, extras: CreationExtras = CreationExtras.Empty): VM = TODO()
@Composable inline fun <reified VM : ViewModel> viewModel(viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" }, key: String? = null, noinline initializer: CreationExtras.() -> VM): VM = TODO()
