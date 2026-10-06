@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.activity
import android.content.Intent
import android.os.Bundle
open class ComponentActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent) }
}
class SystemBarStyle
fun ComponentActivity.enableEdgeToEdge(statusBarStyle: SystemBarStyle = TODO(), navigationBarStyle: SystemBarStyle = TODO()) {}
