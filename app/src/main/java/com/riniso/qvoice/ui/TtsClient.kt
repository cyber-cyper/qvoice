package com.riniso.qvoice.ui

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.concurrent.atomic.AtomicInteger

/**
 * QVoice's UI talking to QVoice's engine through the public TextToSpeech API,
 * bound by package name — exactly the path a sister app takes. So "Try it"
 * exercises the real service (voice negotiation, streaming, stop), not an
 * in-process shortcut. This class is also the seed of the small helper the
 * sister apps will get.
 *
 * Callbacks arrive on binder threads.
 */
class TtsClient(context: Context, private val events: Events) {

    interface Events {
        fun onReady(success: Boolean)
        fun onStart(utteranceId: String)
        fun onDone(utteranceId: String)
        fun onError(utteranceId: String, errorCode: Int)
        fun onStopped(utteranceId: String)
    }

    private val counter = AtomicInteger()

    @Volatile
    var isReady: Boolean = false
        private set

    // The init listener can fire synchronously from inside this constructor
    // when binding fails, before `tts` is assigned — so it must not touch `tts`.
    private val tts: TextToSpeech = TextToSpeech(
        context.applicationContext,
        { status ->
            isReady = status == TextToSpeech.SUCCESS
            events.onReady(isReady)
        },
        context.packageName,
    )

    init {
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    events.onStart(utteranceId.orEmpty())
                }

                override fun onDone(utteranceId: String?) {
                    events.onDone(utteranceId.orEmpty())
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    events.onError(utteranceId.orEmpty(), TextToSpeech.ERROR)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    events.onError(utteranceId.orEmpty(), errorCode)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    events.onStopped(utteranceId.orEmpty())
                }
            },
        )
    }

    /**
     * Speaks [text] with the framework voice named [voiceName] (or the
     * engine's default for the current language when null).
     * @return the utterance id, or null if the request was rejected.
     */
    fun speak(text: String, voiceName: String?, rate: Float, pitch: Float): String? {
        if (!isReady) return null
        if (voiceName != null) {
            val voice = runCatching { tts.voices }.getOrNull()?.firstOrNull { it.name == voiceName }
            if (voice != null) tts.setVoice(voice)
        }
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
        val id = "qv-${counter.incrementAndGet()}"
        return if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id) == TextToSpeech.SUCCESS) id else null
    }

    fun stop() {
        if (isReady) tts.stop()
    }

    /** Package name of the phone's preferred TTS engine, as the user set it in Settings. */
    fun defaultEngine(): String? = runCatching { tts.defaultEngine }.getOrNull()

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }
}
