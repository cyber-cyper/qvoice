package com.riniso.qvoice.ui
// Sandbox-only stand-in for the Compose activity (AndroidX is not available
// here), with the same public members the rest of the app uses.
class MainActivity : android.app.Activity() {
    enum class Screen { HOME, VOICES, ABOUT }

    companion object {
        const val EXTRA_SCREEN = "com.riniso.qvoice.SCREEN"
    }
}
