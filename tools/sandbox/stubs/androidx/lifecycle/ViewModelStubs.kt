package androidx.lifecycle
// Sandbox-only stand-ins with the real AndroidX signatures (lifecycle-viewmodel 2.9).
abstract class ViewModel {
    protected open fun onCleared() {}
}
open class AndroidViewModel(private val application: android.app.Application) : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    open fun <T : android.app.Application> getApplication(): T = application as T
}
