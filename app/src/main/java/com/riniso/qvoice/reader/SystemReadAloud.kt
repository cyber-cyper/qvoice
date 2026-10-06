package com.riniso.qvoice.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.format.DateFormat
import com.riniso.qvoice.service.QVoiceTtsService
import java.util.Date
import java.util.Locale

/** How read-aloud audio is labelled: speech, played as media (volume keys, focus, Bluetooth). */
private val READING_AUDIO: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/**
 * The clock time a sleep timer ends (ReadAloud.State.sleepAt, which runs on
 * SystemClock.elapsedRealtime), as the phone shows times: "23:45" or
 * "11:45 PM". A clock time rather than a countdown, so nothing has to tick.
 */
fun formatSleepTime(context: Context, sleepAt: Long): String =
    DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + (sleepAt - SystemClock.elapsedRealtime())))

/**
 * [Speaker] on QVoice's own engine through the public TextToSpeech API, bound
 * by package name as any reading app would be, so the reader gets the same
 * voice choice, streaming and stopping as every other client.
 *
 * Connects on the first [speak] and holds requests until the connection is
 * up. Callbacks are posted to the main thread, where ReadAloud lives.
 */
class TtsSpeaker(private val context: Context) : Speaker {

    override var listener: Speaker.Listener? = null

    private data class Request(val text: String, val id: String, val flush: Boolean, val rate: Float, val locale: Locale, val ahead: Boolean)

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayList<Request>()
    private var locale: Locale? = null

    override fun speak(text: String, utteranceId: String, flush: Boolean, rate: Float, locale: Locale, ahead: Boolean): Boolean {
        val request = Request(text, utteranceId, flush, rate, locale, ahead)
        if (ready) {
            if (send(request)) return true
            // The connection is gone (the engine was killed, say): drop it, so
            // the next request (Try again) connects afresh.
            shutdown()
            return false
        }
        if (flush) pending.clear()
        pending += request
        connect()
        return true
    }

    override fun stop() {
        pending.clear()
        if (ready) tts?.stop()
    }

    override fun shutdown() {
        pending.clear()
        tts?.run {
            stop()
            shutdown()
        }
        tts = null
        ready = false
        locale = null
    }

    /**
     * TextToSpeech.setLanguage asks the engine for the language's default
     * voice and then names that voice in every request, so a new default is
     * heard only after setLanguage runs again: forgetting the locale makes
     * the next request call it.
     */
    override fun voicesChanged() {
        locale = null
    }

    private fun connect() {
        if (tts != null) return
        // Posted: when binding fails the listener runs inside this constructor,
        // before `tts` is assigned.
        tts = TextToSpeech(context.applicationContext, { status -> main.post { onInit(status) } }, context.packageName)
    }

    private fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            val failed = pending.toList()
            pending.clear()
            engine.shutdown()
            tts = null
            failed.forEach { listener?.onError(it.id, TextToSpeech.ERROR) }
            return
        }
        ready = true
        engine.setAudioAttributes(READING_AUDIO)
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    main.post { listener?.onStart(utteranceId.orEmpty()) }
                }

                override fun onDone(utteranceId: String?) {
                    main.post { listener?.onDone(utteranceId.orEmpty()) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { listener?.onError(utteranceId.orEmpty(), TextToSpeech.ERROR) }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post { listener?.onError(utteranceId.orEmpty(), errorCode) }
                }
            },
        )
        val queued = pending.toList()
        pending.clear()
        queued.forEach { request ->
            if (!send(request)) listener?.onError(request.id, TextToSpeech.ERROR)
        }
    }

    private fun send(request: Request): Boolean {
        val engine = tts ?: return false
        if (request.locale != locale) {
            engine.setLanguage(request.locale)
            locale = request.locale
        }
        engine.setSpeechRate(request.rate)
        val mode = if (request.flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = if (request.ahead) Bundle().apply { putBoolean(QVoiceTtsService.PARAM_QUEUED_AHEAD, true) } else null
        return engine.speak(request.text, mode, params, request.id) == TextToSpeech.SUCCESS
    }
}

/**
 * [PlaybackGuard] on Android's audio focus and the "becoming noisy" broadcast
 * (headphones unplugged, Bluetooth gone), which every audio app should pause
 * for. Asks to be paused rather than ducked when another app beeps: speech
 * turned down under a notification is harder to follow than a short pause.
 */
class SystemPlaybackGuard(context: Context) : PlaybackGuard {

    private val context = context.applicationContext
    private val audio: AudioManager? = this.context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var listener: PlaybackGuard.Listener? = null
    private var noisyRegistered = false

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(READING_AUDIO)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change -> onFocusChange(change) }, main)
            .build()

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) listener?.onPauseRequested(resumeLater = false)
        }
    }

    override fun start(listener: PlaybackGuard.Listener): Boolean {
        this.listener = listener
        val manager = audio ?: return true
        if (manager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            this.listener = null
            return false
        }
        if (!noisyRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(noisy, filter)
            }
            noisyRegistered = true
        }
        return true
    }

    override fun stop() {
        audio?.abandonAudioFocusRequest(focusRequest)
        if (noisyRegistered) {
            context.unregisterReceiver(noisy)
            noisyRegistered = false
        }
        listener = null
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> listener?.onPauseRequested(resumeLater = false)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> listener?.onPauseRequested(resumeLater = true)
            AudioManager.AUDIOFOCUS_GAIN -> listener?.onResumeAllowed()
        }
    }
}
