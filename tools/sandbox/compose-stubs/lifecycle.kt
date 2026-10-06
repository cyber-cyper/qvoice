@file:Suppress("unused")
package androidx.lifecycle
abstract class Lifecycle {
    enum class Event { ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY, ON_ANY }
    enum class State { DESTROYED, INITIALIZED, CREATED, STARTED, RESUMED }
}
interface LifecycleOwner { val lifecycle: Lifecycle }
interface ViewModelStoreOwner
class ViewModelProvider { interface Factory }
