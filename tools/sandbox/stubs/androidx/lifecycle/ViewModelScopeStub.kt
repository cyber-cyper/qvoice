package androidx.lifecycle
// Sandbox-only stand-in with the real signature (lifecycle-viewmodel 2.9:
// val ViewModel.viewModelScope: CoroutineScope, main-dispatcher, cancelled in onCleared).
val ViewModel.viewModelScope: kotlinx.coroutines.CoroutineScope
    get() = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
