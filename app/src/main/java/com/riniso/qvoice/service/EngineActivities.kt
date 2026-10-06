package com.riniso.qvoice.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.riniso.qvoice.QVoiceApp
import com.riniso.qvoice.ui.MainActivity

/**
 * The three small activities Android's TTS settings expect every engine to
 * have. All are UI-less (Theme.NoDisplay) and finish in onCreate, and they are
 * plain android.app.Activity: no AndroidX, nothing to inject.
 */

/**
 * CHECK_TTS_DATA: which languages have voice data installed. Settings hides
 * the engine's language list and "Play" button for languages missing here,
 * so the answer comes from the same voice list the service advertises.
 */
class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as QVoiceApp).graph
        // Bundled voices are known without disk access; a short wait covers
        // downloaded voices on a cold start without risking an ANR.
        graph.voiceStore.awaitFirstScan(500)
        val available = ArrayList(
            graph.exposedVoices.mapNotNull { TtsLocales.checkDataTag(it.languageTag) }.distinct(),
        )
        val data = Intent().apply {
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, available)
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList())
        }
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data)
        finish()
    }
}

/**
 * GET_SAMPLE_TEXT: the sentence Settings reads when the user taps "Play".
 * Settings sends the language as ISO-639-2 ("tam") and expects the result
 * code LANG_AVAILABLE with the text under EXTRA_SAMPLE_TEXT.
 */
class GetSampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = intent?.getStringExtra("language")
        val data = Intent().putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, SampleTexts.forLanguage(language))
        setResult(TextToSpeech.LANG_AVAILABLE, data)
        finish()
    }
}

/**
 * INSTALL_TTS_DATA: Settings' "Install voice data" entry. Opens QVoice's
 * voice library, where voices are downloaded.
 */
class InstallVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SCREEN, MainActivity.Screen.VOICES.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}
