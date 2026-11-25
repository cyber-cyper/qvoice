// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Riniso. Part of "RinisoSpeech", the shared speech helper of
// the Riniso apps. Keep this file IDENTICAL in every app (copy, don't edit);
// the canonical copy lives in the QVoice repository under integration/.
package com.riniso.speech

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.io.File
import java.util.Locale

/**
 * This file's version; the same in every app that carries it.
 * 1.1.0 (26 Nov 2026, from LifeCue's handover): voices tied to their engine
 * (VoiceRef), voice lists across languages and engines, word/sentence ranges
 * passed on, fallback reasons, detection of Android silently binding another
 * engine, the API 33 package lookup, UTM-tagged Play links, ScriptLanguage.
 */
const val RINISO_SPEECH_VERSION = "1.1.0"

/**
 * QVoice (com.riniso.qvoice): Riniso's offline text-to-speech engine. Its
 * voices are reached through Android's ordinary TextToSpeech API; nothing of
 * QVoice is linked into the app (it stays GPL-3.0 on its own; the apps don't).
 *
 * Manifest, required for [isInstalled] and for TextToSpeech to see engines on
 * Android 11+ (package visibility):
 *
 *     <queries>
 *         <package android:name="com.riniso.qvoice" />
 *         <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>
 *     </queries>
 */
object QVoice {
    const val PACKAGE = "com.riniso.qvoice"
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=$PACKAGE"
    private const val PLAY_STORE = "com.android.vending"

    /** Installed and enabled (needs the <queries> entry above). */
    fun isInstalled(context: Context): Boolean = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getApplicationInfo(PACKAGE, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(PACKAGE, 0)
        }
        info.enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * QVoice's Play page with a campaign tag: [source] is the app ("lifecue",
     * "qtune"...), [campaign] where in it ("voice_card"). Play Console counts
     * the installs under Acquisition → Tracked channels (UTM). The tag carries
     * nothing about the user.
     */
    fun playUrl(source: String? = null, campaign: String = "voice_card"): String {
        if (source == null) return PLAY_URL
        val referrer = "utm_source=$source&utm_medium=app&utm_campaign=$campaign"
        return PLAY_URL + "&referrer=" + Uri.encode(referrer)
    }

    /** QVoice's Play page ([playUrl]): in the Play Store app if there is one, else the browser. */
    fun openPlayListing(context: Context, source: String? = null, campaign: String = "voice_card") {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(playUrl(source, campaign))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(context, Intent(view).setPackage(PLAY_STORE))) return
        start(context, view)
    }

    /**
     * Extra for [openVoiceLibrary]: the BCP-47 language the user needs a voice
     * for ("sw", "ml-IN"). QVoice versions that know it open the library on
     * that language's voices; older ones ignore it and show the whole list.
     */
    const val EXTRA_LANGUAGE = "com.riniso.qvoice.extra.LANGUAGE"

    /**
     * QVoice's voice library (Android's standard "install voice data" action,
     * which QVoice answers), to download a voice, for [locale]'s language if
     * given; false if QVoice isn't there.
     */
    fun openVoiceLibrary(context: Context, locale: Locale? = null): Boolean {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        locale?.let { intent.putExtra(EXTRA_LANGUAGE, it.toLanguageTag()) }
        return start(context, intent)
    }

    /** QVoice's home screen (voices, speeds, setting it as the phone's engine). */
    fun openApp(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        return start(context, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/**
 * Speech for a Riniso app: QVoice's voices where QVoice has one for the
 * language, the phone's default engine for everything else, without the app
 * having to care which.
 *
 * - Two engines, each started on first use: QVoice (if installed and the
 *   user's [EngineChoice] allows) and the phone's default.
 * - Every request names its language. QVoice is asked first when it has a
 *   voice for it (SpeechRouting.engineFor); if QVoice refuses the text (a
 *   script it can't read, a voice deleted meanwhile), doesn't start within
 *   [initTimeoutMs], or turns out to be another engine (Android binds the
 *   default engine instead, silently), the same request is repeated on the
 *   system engine, once, and [Callback.onFallback] says why. So adding QVoice
 *   can never make an app go silent.
 * - A voice the user chose ([Request.voice], a [VoiceRef]) names its engine,
 *   so a Google voice stays a Google voice even in Automatic.
 * - Optional: a voice gender, "fast start" for alarms (a fast voice, and a
 *   fallback when the first sound takes longer than [Request.startTimeoutMs]).
 * - Requests on the two engines never talk over each other: one waits until
 *   the other engine has finished.
 * - Callbacks arrive on the main thread.
 *
 * One instance per app (in its Application or DI graph) or per service; call
 * [shutdown] when done. Main thread only.
 */
class RinisoSpeech(
    context: Context,
    /** Read at every request, so a settings change applies at once. */
    private val choice: () -> EngineChoice = { EngineChoice.AUTOMATIC },
    /** Applied to both engines: USAGE_ALARM for alarms, USAGE_MEDIA for reading, etc. */
    private val audioAttributes: AudioAttributes? = null,
    /** How long QVoice may take to start before a request goes to the system engine. */
    private val initTimeoutMs: Long = 4_000L,
) {
    private companion object {
        const val RETRY_AFTER_MS = 60_000L
    }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** What happened to a request. [engine]: who spoke (or tried last). */
    interface Callback {
        fun onStart(id: String, engine: SpeechRouting.Engine) {}

        /**
         * The engine is about to speak characters [start] until [end] of the
         * request's text. Google's engine reports words; QVoice reports nothing
         * yet (planned: its chunks), so keep onStart-based progress as the
         * baseline and treat ranges as a refinement.
         */
        fun onRange(id: String, start: Int, end: Int, engine: SpeechRouting.Engine) {}

        fun onDone(id: String, engine: SpeechRouting.Engine) {}

        /** Meant for QVoice, spoken by the system engine instead; for an app's own logs. */
        fun onFallback(id: String, reason: FallbackReason) {}

        /** Neither engine could speak it. */
        fun onError(id: String) {}
    }

    /**
     * One thing to say. [locale]: its language (the app knows it, or
     * ScriptLanguage.guess finds it for text users wrote). [file]: synthesise
     * into this WAV file instead of speaking (ringtones, alarms made ahead).
     * [flush]: drop whatever is queued.
     */
    class Request(
        val id: String,
        val text: CharSequence,
        val locale: Locale,
        val gender: VoiceGender = VoiceGender.ANY,
        /** A voice the user chose ([VoiceRef], as stored); null: the engine's choice. */
        val voice: VoiceRef? = null,
        /** Alarms and reminders: prefer a voice that starts at once. */
        val fastStart: Boolean = false,
        /** With QVoice: no first sound within this time -> the system engine says it. Null: wait. */
        val startTimeoutMs: Long? = null,
        val flush: Boolean = false,
        val file: File? = null,
        /** Speech rate and pitch, 1 = normal. */
        val rate: Float = 1f,
        val pitch: Float = 1f,
        val callback: Callback? = null,
    )

    /** A voice for a settings list. Store [ref] (its toString()) and pass it back as [Request.voice]. */
    class VoiceOption(
        val ref: VoiceRef,
        val label: String,
        val locale: Locale,
        val gender: VoiceGender,
        /** Starts at once; false for QVoice's large voices (Kokoro, Supertonic). */
        val fast: Boolean,
    ) {
        val engine: SpeechRouting.Engine get() = ref.engine
    }

    private inner class Engine(val kind: SpeechRouting.Engine) {
        var tts: TextToSpeech? = null
        var ready = false
        var failed = false
        var failure: FallbackReason? = null
        private var failedAt = 0L
        val waiting = ArrayList<(TextToSpeech?) -> Unit>()

        /** Runs [action] with the started engine, or with null if it failed or (QVoice) is too slow. */
        fun use(action: (TextToSpeech?) -> Unit) {
            // A failed start is tried again after a minute (QVoice updated,
            // re-enabled, or the phone unlocked after a restart), not forever.
            if (failed && SystemClock.elapsedRealtime() - failedAt > RETRY_AFTER_MS) shutdown()
            if (failed) return action(null)
            if (ready) return action(tts)
            waiting += action
            if (tts == null) start()
        }

        private fun start() {
            val listener = TextToSpeech.OnInitListener { status ->
                main.post {
                    val engine = tts ?: return@post // shut down meanwhile
                    when {
                        status != TextToSpeech.SUCCESS -> fail(FallbackReason.QVOICE_FAILED_TO_START)
                        // Android binds the default engine when the requested one
                        // can't start, and still reports SUCCESS: check it's QVoice.
                        kind == SpeechRouting.Engine.QVOICE && !SpeechRouting.isQVoice(safeVoices(engine)) ->
                            fail(FallbackReason.QVOICE_REPLACED_BY_ANDROID)
                        else -> ready = true
                    }
                    flushWith(if (ready) tts else null)
                }
            }
            tts = if (kind == SpeechRouting.Engine.QVOICE) TextToSpeech(app, listener, QVoice.PACKAGE) else TextToSpeech(app, listener)
            tts!!.setOnUtteranceProgressListener(progress)
            audioAttributes?.let { tts!!.setAudioAttributes(it) }
            if (kind == SpeechRouting.Engine.QVOICE) {
                // Too slow to start (a phone waking from deep sleep, a huge
                // voice): what waits goes to the system engine; later requests
                // still try QVoice once it is up.
                main.postDelayed({ if (!ready && !failed) flushWith(null) }, initTimeoutMs)
            }
        }

        private fun fail(reason: FallbackReason) {
            failed = true
            failure = reason
            failedAt = SystemClock.elapsedRealtime()
            tts?.shutdown()
        }

        private fun flushWith(engine: TextToSpeech?) {
            val now = ArrayList(waiting)
            waiting.clear()
            now.forEach { it(engine) }
        }

        fun shutdown() {
            tts?.shutdown()
            tts = null
            ready = false
            failed = false
            failure = null
            waiting.clear()
        }
    }

    private val qvoice = Engine(SpeechRouting.Engine.QVOICE)
    private val system = Engine(SpeechRouting.Engine.SYSTEM)

    private fun engineOf(kind: SpeechRouting.Engine) = if (kind == SpeechRouting.Engine.QVOICE) qvoice else system

    /** A request on its way: which engine has it, under which internal id. */
    private class Pending(val request: Request, val engine: SpeechRouting.Engine, val seq: Int, var started: Boolean = false)

    private val pending = HashMap<String, Pending>()
    private var counter = 0

    /**
     * Spoken requests waiting for the other engine to finish. Each engine has
     * its own queue, so a Tamil line on the phone's engine followed by an
     * English one on QVoice would otherwise be spoken at the same time; the
     * English one waits here until the Tamil one is done. Requests for the
     * same engine still queue inside it (no gap between them).
     */
    private val held = ArrayDeque<Pair<Request, Engine>>()

    /** Raised by [stop]: requests sent before it and still waiting for an engine are dropped. */
    private var generation = 0

    /** Spoken requests handed to an engine that is still starting (not yet in [pending]). */
    private val starting = HashMap<SpeechRouting.Engine, Int>()

    /** Says (or renders, with [Request.file]) [request]. */
    fun speak(request: Request) {
        if (request.flush) stop()
        val installed = QVoice.isInstalled(app)
        val pinned = request.voice?.engine
        val wanted = SpeechRouting.engineFor(choice(), installed, qvoiceHasLanguage = null, pinned = pinned)
        if (wanted == SpeechRouting.Engine.SYSTEM) return send(request, system)
        val sentIn = generation
        qvoice.use { tts ->
            if (sentIn != generation) return@use
            if (tts == null) {
                qvoice.failure?.let { request.callback?.onFallback(request.id, it) }
                    ?: request.callback?.onFallback(request.id, FallbackReason.QVOICE_SLOW_TO_START)
                return@use send(request, system)
            }
            // A chosen QVoice voice is spoken whatever the language; otherwise
            // only languages QVoice has.
            val has = if (pinned == SpeechRouting.Engine.QVOICE) true else hasLanguage(tts, request.locale)
            val engine = SpeechRouting.engineFor(choice(), installed, has, pinned)
            send(request, engineOf(engine))
        }
    }

    /** Starts the engines a [locale] needs now, so the first word later comes at once (alarms: call when the alarm fires, before building the text). */
    fun warmUp(locale: Locale) {
        // setLanguage makes an engine load that language's voice.
        system.use { tts -> tts?.setLanguage(locale) }
        if (SpeechRouting.engineFor(choice(), QVoice.isInstalled(app), null) == SpeechRouting.Engine.QVOICE) {
            qvoice.use { tts -> if (tts != null && hasLanguage(tts, locale)) tts.setLanguage(locale) }
        }
    }

    /**
     * The voices a settings screen can offer: the phone engine's and, when
     * QVoice is installed, QVoice's, for [locale]'s language or (null) every
     * language. Installed, offline voices only; QVoice's first. Whatever the
     * user's EngineChoice: the list is where they choose.
     */
    fun voices(locale: Locale?, onResult: (List<VoiceOption>) -> Unit) {
        fun options(kind: SpeechRouting.Engine, tts: TextToSpeech?): List<VoiceOption> = tts?.let { engine ->
            val raw = try { engine.voices.orEmpty() } catch (e: Exception) { emptySet<Voice>() }
            raw.filter { v ->
                val info = v.toInfo()
                !info.requiresNetwork && !info.notInstalled &&
                    (locale == null || info.language.equals(locale.language, ignoreCase = true))
            }.map { v ->
                val info = v.toInfo()
                VoiceOption(
                    ref = VoiceRef(kind, v.name),
                    label = if (kind == SpeechRouting.Engine.QVOICE) SpeechRouting.labelOf(v.name) else v.name,
                    locale = v.locale,
                    gender = SpeechRouting.genderOf(info),
                    fast = SpeechRouting.isFast(info),
                )
            }.sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.label }))
        }.orEmpty()

        system.use { sys ->
            val systemList = options(SpeechRouting.Engine.SYSTEM, sys)
            if (!QVoice.isInstalled(app)) return@use onResult(systemList)
            qvoice.use { qv -> onResult(options(SpeechRouting.Engine.QVOICE, qv) + systemList) }
        }
    }

    /**
     * Which engines have a voice for [locale]'s language, for text users
     * write in any language (a cue in Swahili): when neither has, the app can
     * say so and offer QVoice.openVoiceLibrary(context, locale) (or Android's
     * own "install voice data") instead of failing silently at alarm time.
     */
    fun canSpeak(locale: Locale, onResult: (qvoice: Boolean, system: Boolean) -> Unit) {
        system.use { sys ->
            val onSystem = sys?.let { hasLanguage(it, locale) } ?: false
            if (!QVoice.isInstalled(app)) return@use onResult(false, onSystem)
            qvoice.use { qv -> onResult(qv?.let { hasLanguage(it, locale) } ?: false, onSystem) }
        }
    }

    /** Stops everything being said or queued, on both engines. */
    fun stop() {
        pending.clear()
        held.clear()
        // Requests still waiting for an engine to start are dropped when it does.
        generation++
        qvoice.tts?.takeIf { qvoice.ready }?.stop()
        system.tts?.takeIf { system.ready }?.stop()
    }

    fun shutdown() {
        pending.clear()
        held.clear()
        generation++
        main.removeCallbacksAndMessages(null)
        qvoice.shutdown()
        system.shutdown()
    }

    // ---- internals ----

    private fun send(request: Request, engine: Engine) {
        if (request.file == null && (held.isNotEmpty() || speakingOn(other(engine.kind)))) {
            held.addLast(request to engine)
            return
        }
        dispatch(request, engine)
    }

    /** Hands [request] to [engine] now (the order checks are done). */
    private fun dispatch(request: Request, engine: Engine) {
        val spoken = request.file == null
        if (spoken) starting[engine.kind] = (starting[engine.kind] ?: 0) + 1
        val sentIn = generation
        engine.use { tts ->
            if (spoken) starting[engine.kind] = (starting[engine.kind] ?: 1) - 1
            if (sentIn != generation) return@use releaseHeld() // stopped meanwhile
            if (tts == null) {
                // QVoice failed or is too slow: the system engine. The system
                // engine failing too: report it.
                if (engine.kind == SpeechRouting.Engine.QVOICE) {
                    request.callback?.onFallback(request.id, engine.failure ?: FallbackReason.QVOICE_SLOW_TO_START)
                    return@use dispatch(request, system)
                }
                request.callback?.onError(request.id)
                return@use releaseHeld()
            }
            val key = "rs${++counter}"
            pending[key] = Pending(request, engine.kind, counter)
            configure(tts, request, engine.kind)
            val result = if (request.file != null) {
                tts.synthesizeToFile(request.text, Bundle(), request.file, key)
            } else {
                tts.speak(request.text, TextToSpeech.QUEUE_ADD, Bundle(), key)
            }
            if (result != TextToSpeech.SUCCESS) {
                failed(key, FallbackReason.QVOICE_REFUSED)
            } else if (engine.kind == SpeechRouting.Engine.QVOICE && request.file == null) {
                request.startTimeoutMs?.let { ms ->
                    main.postDelayed({ if (pending[key]?.started == false) failed(key, FallbackReason.QVOICE_NO_SOUND_IN_TIME, stopEngine = true) }, ms)
                }
            }
        }
    }

    private fun other(kind: SpeechRouting.Engine) =
        if (kind == SpeechRouting.Engine.QVOICE) SpeechRouting.Engine.SYSTEM else SpeechRouting.Engine.QVOICE

    private fun speakingOn(kind: SpeechRouting.Engine) =
        (starting[kind] ?: 0) > 0 || pending.values.any { it.request.file == null && it.engine == kind }

    /** Sends what was held for the other engine, in order, once that engine has nothing left to say. */
    private fun releaseHeld() {
        while (held.isNotEmpty()) {
            val (request, engine) = held.first()
            if (speakingOn(other(engine.kind))) return
            held.removeFirst()
            dispatch(request, engine)
        }
    }

    /** Voice (chosen, by gender, by speed need) or the engine's default for the language; rate; pitch. */
    private fun configure(tts: TextToSpeech, request: Request, kind: SpeechRouting.Engine) {
        // setLanguage on every request: it also brings back the engine's
        // default voice after a setVoice, and picks up a default the user
        // changed in QVoice meanwhile (TextToSpeech caches the voice name).
        tts.setLanguage(request.locale)
        val voices = safeVoices(tts)
        val name = SpeechRouting.pickVoice(
            voices = voices,
            language = request.locale.language,
            country = request.locale.country,
            gender = request.gender,
            // A chosen voice counts only on its own engine (after a fallback,
            // the system engine uses its default for the language).
            preferredName = request.voice?.takeIf { it.engine == kind }?.name,
            engineDefaultName = try { tts.voice?.name } catch (e: Exception) { null },
            fastStart = request.fastStart && kind == SpeechRouting.Engine.QVOICE,
        )
        if (name != null) {
            try { tts.voices?.firstOrNull { it.name == name } } catch (e: Exception) { null }?.let { tts.setVoice(it) }
        }
        tts.setSpeechRate(request.rate)
        tts.setPitch(request.pitch)
    }

    private fun hasLanguage(tts: TextToSpeech, locale: Locale): Boolean =
        tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE

    /** Some engines throw or return null from getVoices(). */
    private fun safeVoices(tts: TextToSpeech): List<SpeechRouting.VoiceInfo> = try {
        tts.voices.orEmpty().map { it.toInfo() }
    } catch (e: Exception) {
        emptyList()
    }

    private fun Voice.toInfo() = SpeechRouting.VoiceInfo(
        name = name,
        language = locale?.language.orEmpty(),
        country = locale?.country.orEmpty(),
        features = features.orEmpty(),
        latency = latency,
        requiresNetwork = isNetworkConnectionRequired,
        notInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in features.orEmpty(),
    )

    /** A request [key] failed where it was: once on the system engine if QVoice had it, else reported. */
    private fun failed(key: String, reason: FallbackReason, stopEngine: Boolean = false) {
        val p = pending.remove(key) ?: return
        if (p.engine == SpeechRouting.Engine.QVOICE) {
            p.request.callback?.onFallback(p.request.id, reason)
            // Spoken requests QVoice holds after this one would otherwise be
            // heard before its fallback: take them back, stop QVoice, and
            // send them again in their order, the failed one (on the phone's
            // engine) first.
            val later = if (p.request.file != null) emptyList() else pending.entries
                .filter { it.value.engine == SpeechRouting.Engine.QVOICE && it.value.request.file == null && it.value.seq > p.seq }
                .sortedBy { it.value.seq }
            later.forEach { pending.remove(it.key) }
            if (stopEngine || later.isNotEmpty()) qvoice.tts?.stop()
            later.asReversed().forEach { held.addFirst(it.value.request to qvoice) }
            held.addFirst(p.request to system)
        } else {
            p.request.callback?.onError(p.request.id)
        }
        releaseHeld()
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            main.post {
                val p = pending[utteranceId] ?: return@post
                p.started = true
                p.request.callback?.onStart(p.request.id, p.engine)
            }
        }

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            main.post {
                val p = pending[utteranceId] ?: return@post
                p.request.callback?.onRange(p.request.id, start, end, p.engine)
            }
        }

        override fun onDone(utteranceId: String) {
            main.post {
                val p = pending.remove(utteranceId) ?: return@post
                p.request.callback?.onDone(p.request.id, p.engine)
                releaseHeld()
            }
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            main.post { failed(utteranceId, FallbackReason.QVOICE_REFUSED) }
        }

        @Deprecated("Android calls onError(String, Int) on API 21+")
        override fun onError(utteranceId: String) {
            main.post { failed(utteranceId, FallbackReason.QVOICE_REFUSED) }
        }
    }
}
