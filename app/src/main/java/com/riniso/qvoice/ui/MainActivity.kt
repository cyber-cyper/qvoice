package com.riniso.qvoice.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The main activity (the reader has its own). Four screens don't justify
 * Navigation Compose, so a saveable value switches between them and the
 * system back gesture returns to Home, or from Help to where Help was opened
 * (Home or About). [EXTRA_SCREEN] opens a given screen (Android's TTS
 * settings "Install voice data" opens the voice library).
 */
class MainActivity : ComponentActivity() {

    enum class Screen { HOME, VOICES, ABOUT, HELP }

    /** A screen asked for by the launching intent, consumed once shown. */
    private val requested = mutableStateOf<Screen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge is enforced at targetSdk 35+; Scaffold insets handle the bars.
        enableEdgeToEdge()
        if (savedInstanceState == null) requested.value = screenFrom(intent)
        setContent {
            QVoiceTheme {
                // Starts on the screen the intent asked for, so opening the
                // voice library from Android's TTS settings doesn't draw (and
                // set up) Home first for a frame.
                var screen by rememberSaveable { mutableStateOf(requested.value ?: Screen.HOME) }
                // Where Help goes back to: it opens from Home and from About.
                var helpReturn by rememberSaveable { mutableStateOf(Screen.HOME) }
                val openHelp = { from: Screen ->
                    helpReturn = from
                    screen = Screen.HELP
                }
                val asked by requested
                LaunchedEffect(asked) {
                    asked?.let {
                        screen = it
                        requested.value = null
                    }
                }
                BackHandler(enabled = screen != Screen.HOME) {
                    screen = if (screen == Screen.HELP) helpReturn else Screen.HOME
                }
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        onOpenAbout = { screen = Screen.ABOUT },
                        onOpenVoices = { screen = Screen.VOICES },
                        onOpenHelp = { openHelp(Screen.HOME) },
                    )
                    Screen.VOICES -> VoicesScreen(onBack = { screen = Screen.HOME })
                    Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.HOME }, onOpenHelp = { openHelp(Screen.ABOUT) })
                    Screen.HELP -> HelpScreen(onBack = { screen = helpReturn }, onOpenVoices = { screen = Screen.VOICES })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requested.value = screenFrom(intent)
    }

    private fun screenFrom(intent: Intent?): Screen? =
        intent?.getStringExtra(EXTRA_SCREEN)?.let { name -> Screen.entries.firstOrNull { it.name == name } }

    companion object {
        const val EXTRA_SCREEN = "com.riniso.qvoice.SCREEN"
    }
}
