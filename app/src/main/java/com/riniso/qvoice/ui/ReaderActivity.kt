package com.riniso.qvoice.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.riniso.qvoice.AppGraph
import com.riniso.qvoice.QVoiceApp
import com.riniso.qvoice.reader.ReadAloud
import com.riniso.qvoice.reader.SharedText
import com.riniso.qvoice.service.TtsLocales
import java.util.Locale
import kotlin.concurrent.thread

/**
 * The reader, opened by "Read aloud" on a text selection in any app
 * (ACTION_PROCESS_TEXT), by sharing text or a text file to QVoice
 * (ACTION_SEND), from Home (with nothing to read yet it offers a paste
 * field; with a text, "Continue listening" opens it reading,
 * [ACTION_CONTINUE]), from its launcher shortcuts ("Read copied text" reads
 * the clipboard, [ACTION_READ_CLIPBOARD]), or from the reading
 * notification. Reading itself happens in AppGraph.readAloud, which also
 * remembers the text and place across restarts (ReaderMemory); this only
 * shows and drives it.
 *
 * One instance, in a task of its own (singleTask with its own affinity, see
 * the manifest): the notification and Home then return to the same reader,
 * and leaving it for the app the text came from doesn't leave the reader
 * stacked on top of that app.
 *
 * Exported by design, like every share target: any app may hand it text,
 * and all it does with the text is show it and read it aloud.
 */
class ReaderActivity : ComponentActivity() {

    private val graph by lazy { (application as QVoiceApp).graph }

    /**
     * Why a text handed over wasn't read (a bare web address, a file that
     * isn't text, an empty or private clipboard): above the reader's text,
     * or in the paste field when it holds none.
     */
    private val notice = mutableStateOf<ShareNotice?>(null)

    /** Bumped by every share, so a slow file read can't overwrite a newer share. */
    private var shares = 0

    /** The reader's text size (QVoiceSettings.readerTextScale), read once and kept in step with the menu. */
    private val textScale by lazy { mutableStateOf(graph.settings.readerTextScale) }

    /**
     * "Read copied text" is waiting for the window's focus: Android 10+ lets
     * only the focused app read the clipboard, and an activity being started
     * doesn't have it yet.
     */
    private var pasteOnFocus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Recreated after the process was killed in the background: the
        // reader normally has its text and place back from ReaderMemory; if
        // that memory is missing, the intent's text is loaded again instead.
        // Either way, no talking unasked.
        if (savedInstanceState == null || graph.readAloud.state.value.status == ReadAloud.Status.EMPTY) {
            handle(intent, play = savedInstanceState == null)
        }
        setContent {
            QVoiceTheme {
                ReaderScreen(
                    reader = graph.readAloud,
                    notice = notice.value,
                    onDismissNotice = { notice.value = null },
                    onReadClipboardAnyway = { readClipboard(allowSensitive = true) },
                    onClose = { finish() },
                    onOpenVoices = {
                        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, MainActivity.Screen.VOICES.name))
                    },
                    voicesFor = ::voicesFor,
                    onChooseVoice = { row ->
                        graph.settings.makeDefault(row.voiceName, row.languageTag, row.language)
                        graph.readAloud.voiceChanged()
                    },
                    textScale = textScale.value,
                    onTextScale = { scale ->
                        graph.settings.readerTextScale = scale
                        textScale.value = scale
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Only a new text replaces the intent kept for after a process death:
        // the notification, Home and the shortcut bring the reader back with
        // a bare intent, and "Continue listening" must not replay itself when
        // the screen is recreated.
        if (fileOf(intent) != null || textOf(intent) != null) setIntent(intent)
        handle(intent, play = true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pasteOnFocus) {
            pasteOnFocus = false
            readClipboard(allowSensitive = false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Closed while reading: the reading goes on in the background, with
        // its notification (ReadAloudService), as with any audio app. Closed
        // while paused or done: the listening session ends here.
        if (isFinishing && graph.readAloud.state.value.status != ReadAloud.Status.PLAYING) graph.readAloud.stop()
    }

    /**
     * Every voice for [locale]'s language, the one the engine picks for it
     * (the language's default for this region) marked: the voice reading it.
     */
    private fun voicesFor(locale: Locale): List<VoiceRow> {
        val language = TtsLocales.toIso2Language(locale.language) ?: return emptyList()
        val current = graph.selector.defaultFor(language, TtsLocales.toIso2Region(locale.country))?.name
        return VoiceRow.listFor(language, graph.exposedVoices, current, graph::speedEstimate)
    }

    private fun handle(intent: Intent, play: Boolean) {
        // Whatever came since "Read copied text" was tapped replaces it.
        pasteOnFocus = false
        val file = fileOf(intent)
        if (file == null) {
            val text = textOf(intent)
            when {
                text != null -> {
                    shares++
                    load(text, play)
                }
                // Home's "Continue listening": the text the reader holds, from where it is.
                intent.action == ACTION_CONTINUE && play -> graph.readAloud.play()
                // Only when just asked: never again when the screen is recreated.
                intent.action == ACTION_READ_CLIPBOARD && play -> {
                    if (hasWindowFocus()) readClipboard(allowSensitive = false) else pasteOnFocus = true
                }
                // A bare intent (Home, the notification, the Read aloud shortcut) changes nothing.
            }
            return
        }
        // A shared file: its content, not the text that may come with it
        // (often just its name). Read off the main thread: it comes through
        // the sharing app's content provider.
        val share = ++shares
        thread(name = "qvoice-share", isDaemon = true) {
            val text = readFile(file)
            runOnUiThread {
                // Superseded by a newer share, or the reader was closed meanwhile.
                if (share != shares || isFinishing || isDestroyed) return@runOnUiThread
                val sharedText = textOf(intent)
                when {
                    text != null -> load(text, play)
                    sharedText != null -> load(sharedText, play)
                    else -> notice.value = ShareNotice.UNREADABLE_FILE
                }
            }
        }
    }

    /**
     * A new text replaces the reader's; a share that can't be read (a web
     * address alone, an unreadable file) only says why: the text the reader
     * holds, and its reading, carry on.
     */
    private fun load(text: CharSequence, play: Boolean) {
        if (isOnlyALink(text)) {
            notice.value = ShareNotice.LINK_ONLY
            return
        }
        notice.value = null
        graph.readAloud.load(text, play = play)
    }

    /**
     * Reads the clipboard's text aloud ("Read copied text"), or says why not:
     * nothing to read, or a clip its app marked sensitive (a password), which
     * is read only when the user asks again ([allowSensitive]).
     */
    private fun readClipboard(allowSensitive: Boolean) {
        when (val clip = ClipboardText.read(this, allowSensitive)) {
            is ClipboardText.Result.Text -> {
                shares++
                load(clip.text, play = true)
            }
            ClipboardText.Result.Sensitive -> notice.value = ShareNotice.CLIPBOARD_SENSITIVE
            ClipboardText.Result.Empty -> notice.value = ShareNotice.CLIPBOARD_EMPTY
        }
    }

    /** The file's text, or null if it can't be read or isn't text. */
    private fun readFile(uri: Uri): String? = try {
        contentResolver.openInputStream(uri)?.use { SharedText.decode(SharedText.readUpTo(it)) }
            ?.takeIf { SharedText.looksLikeText(it) }
    } catch (e: Exception) {
        // SecurityException (no read grant), FileNotFoundException, IOException.
        Log.w(AppGraph.TAG, "Read aloud: couldn't read the shared file", e)
        null
    }

    companion object {
        /** Home's "Continue listening": open the reader and read on from its place. */
        const val ACTION_CONTINUE = "com.riniso.qvoice.action.CONTINUE_READING"

        /** The "Read copied text" launcher shortcut (res/xml/shortcuts.xml names it too). */
        const val ACTION_READ_CLIPBOARD = "com.riniso.qvoice.action.READ_CLIPBOARD"

        /**
         * The text a selection or a share hands over; null for any other
         * launch (from Home: the reader then keeps what it had). A share's
         * subject, when it isn't already in the text, is read first.
         */
        fun textOf(intent: Intent): CharSequence? {
            val text = when (intent.action) {
                Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                else -> null
            }?.takeIf { it.isNotBlank() } ?: return null
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()
            return if (intent.action == Intent.ACTION_SEND && !subject.isNullOrEmpty() && !text.contains(subject)) {
                "$subject\n\n$text"
            } else {
                text
            }
        }

        /** A shared file (Intent.EXTRA_STREAM of a text share), or null. */
        fun fileOf(intent: Intent): Uri? {
            if (intent.action != Intent.ACTION_SEND) return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        }

        private val LINK = Regex("\\s*https?://\\S+\\s*")

        /**
         * Chrome and most apps share a web page as its address alone, or as a
         * title line and the address; reading the address aloud helps no one.
         */
        fun isOnlyALink(text: CharSequence): Boolean {
            if (LINK.matches(text)) return true
            val lines = text.lines().filter { it.isNotBlank() }
            return lines.size == 2 && lines[0].length <= MAX_TITLE && LINK.matches(lines[1])
        }

        private const val MAX_TITLE = 150
    }
}
