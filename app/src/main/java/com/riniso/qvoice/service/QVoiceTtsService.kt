package com.riniso.qvoice.service

import android.content.ComponentCallbacks2
import android.media.AudioFormat
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import com.riniso.qvoice.AppGraph
import com.riniso.qvoice.QVoiceApp
import com.riniso.qvoice.engine.PcmStreamer
import com.riniso.qvoice.engine.Prosody
import com.riniso.qvoice.engine.ReadAhead
import com.riniso.qvoice.engine.SpeechModel
import com.riniso.qvoice.engine.TempoStretcher
import com.riniso.qvoice.voices.Gender
import com.riniso.qvoice.voices.VoiceFamily
import com.riniso.qvoice.voices.VoiceQuality
import com.riniso.qvoice.voices.VoiceStore

/**
 * QVoice as a system text-to-speech engine.
 *
 * Any app that uses android.speech.tts.TextToSpeech reaches this service —
 * either because the user made QVoice the default engine, or because the app
 * asked for it by package name (`TextToSpeech(context, listener,
 * "com.riniso.qvoice")`), which is how the Riniso sister apps use it without
 * changing the user's system default.
 *
 * Threading, as the framework runs it:
 * - onSynthesizeText runs on the framework's single synthesis thread, one
 *   request at a time; blocking it (model load, synthesis) is expected.
 *   Since slice 3 the model generates on a second thread ([ReadAhead]) while
 *   this one feeds the framework at playback speed, so sentences no longer
 *   wait for the previous one to finish playing.
 * - onStop arrives on a binder thread *while* onSynthesizeText is running;
 *   it flags the request and cancels its ReadAhead, which stops generation
 *   at the next sentence boundary.
 * - the negotiation calls (onIsLanguageAvailable, onGetVoices, ...) arrive on
 *   binder threads and answer from in-memory snapshots; they never load a
 *   model or read voice files.
 *
 * Deliberately NOT protected by android:permission in the manifest: a TTS
 * engine is public API, and on Android 11 and older the client app binds the
 * engine itself, so any permission (HayaiTTS used BIND_TEXT_SERVICE, a
 * system-only permission) makes TextToSpeech() fail with "Not allowed to
 * bind to service" for every ordinary app.
 */
class QVoiceTtsService : TextToSpeechService() {

    /**
     * Lazy, never `lateinit` assigned in onCreate: the framework's
     * TextToSpeechService.onCreate() itself calls onLoadLanguage() for the
     * default locale, synchronously, before control returns to our onCreate.
     * Slice 1 assigned the graph after super.onCreate(), so that call hit an
     * uninitialised property and the app crashed the moment its own screen
     * bound the engine. tools/check_project.py now rejects lateinit here.
     */
    private val graph: AppGraph by lazy { (application as QVoiceApp).graph }

    /** Per-request stop state; a fresh one per request so a late onStop can't stop the next one. */
    private class SynthJob {
        @Volatile var stopped = false
            private set

        /** When onStop arrived (elapsedRealtime), for the log's "ended N ms after the stop request". */
        @Volatile var stopRequestedAt = 0L
            private set

        @Volatile private var readAhead: ReadAhead? = null

        /** onStop (binder thread): the client stopped this request. */
        fun requestStop() {
            if (stopRequestedAt == 0L) stopRequestedAt = SystemClock.elapsedRealtime()
            stopped = true
            readAhead?.cancel()
        }

        /** The framework refused audio (client stopped or went away): end the request. */
        fun abandon() {
            stopped = true
            readAhead?.cancel()
        }

        /**
         * Connects the request's ReadAhead. Writes [readAhead] before reading
         * [stopped], the mirror image of [requestStop]; with both fields
         * volatile, a stop racing this call is seen by at least one side.
         */
        fun attach(r: ReadAhead) {
            readAhead = r
            if (stopped) r.cancel()
        }
    }

    @Volatile private var currentJob: SynthJob? = null

    /** What onGetLanguage reports: the language of the voice last loaded. */
    @Volatile private var loadedLanguage: Array<String> = arrayOf("eng", "USA", "")

    @Volatile private var frameworkVoices: List<Voice> = emptyList()

    private val storeListener = VoiceStore.Listener { rebuildFrameworkVoices() }

    override fun onCreate() {
        // Our state first, the framework's last: super.onCreate() calls
        // onLoadLanguage() (see [graph]), so everything it may touch must be
        // ready before it runs.
        graph.voiceStore.addListener(storeListener)
        rebuildFrameworkVoices()
        super.onCreate()
    }

    override fun onDestroy() {
        graph.voiceStore.removeListener(storeListener)
        super.onDestroy()
        // No client is bound any more; give the model memory back. The next
        // client pays one model load (about half a second for the bundled voice).
        graph.engineHost.releaseIdle()
    }

    @Suppress("DEPRECATION") // RUNNING_CRITICAL is still delivered on older releases.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            graph.engineHost.releaseIdle()
        }
    }

    // ------------------------------------------------------------------
    // Language and voice negotiation (binder threads, answered from memory)
    // ------------------------------------------------------------------

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        awaitVoices()
        return when (graph.selector.availability(lang, country)) {
            VoiceSelector.Availability.NOT_SUPPORTED -> TextToSpeech.LANG_NOT_SUPPORTED
            VoiceSelector.Availability.LANGUAGE -> TextToSpeech.LANG_AVAILABLE
            VoiceSelector.Availability.LANGUAGE_AND_REGION -> TextToSpeech.LANG_COUNTRY_AVAILABLE
        }
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val result = onIsLanguageAvailable(lang, country, variant)
        if (result >= TextToSpeech.LANG_AVAILABLE) {
            graph.selector.defaultFor(lang, country)?.let { voice ->
                loadedLanguage = TtsLocales.frameworkTriple(voice.languageTag)
                warmUp(voice)
            }
        }
        return result
    }

    override fun onGetLanguage(): Array<String> = loadedLanguage

    override fun onGetVoices(): List<Voice> {
        awaitVoices()
        return frameworkVoices
    }

    override fun onIsValidVoiceName(voiceName: String?): Int {
        awaitVoices()
        return if (graph.selector.findByName(voiceName) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    }

    override fun onLoadVoice(voiceName: String?): Int {
        awaitVoices()
        val voice = graph.selector.findByName(voiceName) ?: return TextToSpeech.ERROR
        loadedLanguage = TtsLocales.frameworkTriple(voice.languageTag)
        warmUp(voice)
        return TextToSpeech.SUCCESS
    }

    /**
     * Must name a voice that [onGetVoices] lists and [onLoadVoice] accepts:
     * TextToSpeech.setLanguage() looks the answer up in the voice list and
     * reports LANG_NOT_SUPPORTED if it isn't there.
     */
    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        awaitVoices()
        return graph.selector.defaultFor(lang, country)?.name
    }

    // ------------------------------------------------------------------
    // Synthesis (framework synthesis thread)
    // ------------------------------------------------------------------

    override fun onStop() {
        currentJob?.requestStop()
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val job = SynthJob()
        currentJob = job
        try {
            synthesize(request, callback, job)
        } finally {
            if (currentJob === job) currentJob = null
        }
    }

    private fun synthesize(request: SynthesisRequest, callback: SynthesisCallback, job: SynthJob) {
        val text = request.charSequenceText?.toString().orEmpty()
        // QVoice's reader marks a sentence it queues while another plays: no
        // fast-start cut for it (SpeechModel.generate's opening). Any client
        // may set it; nobody else is affected.
        val opening = request.params?.getBoolean(PARAM_QUEUED_AHEAD, false) != true
        awaitVoices()
        val caller = callerOf(request)
        val chosen = when (val r = graph.selector.resolve(request.voiceName, request.language, request.country, text)) {
            is VoiceSelector.Resolution.Refuse -> {
                Log.w(TAG, "Refusing request from $caller: ${r.why}")
                // ERROR_NOT_INSTALLED_YET tells the client the language's
                // voice data is missing, so it can fall back to another engine.
                callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
                return
            }
            is VoiceSelector.Resolution.Speak -> r
        }
        val exposed = chosen.voice

        if (text.isBlank()) {
            // Nothing to say, but the client still waits for onDone.
            if (callback.start(exposed.voice.manifest.sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1) ==
                TextToSpeech.SUCCESS
            ) {
                callback.done()
            }
            return
        }

        val t0 = SystemClock.elapsedRealtime()
        val prosody = Prosody.of(request.speechRate, request.pitch)
        try {
            val voice = graph.voiceStore.prepare(exposed.voice) // first use: copies the eSpeak NG data
            val variant = variantFor(exposed)
            var modelReadyMs = -1L
            var firstAudioMs = -1L
            var sampleRate = 0
            var threads = 0
            var result: ReadAhead.Result? = null
            graph.engineHost.withModel(voice, variant) { model ->
                modelReadyMs = SystemClock.elapsedRealtime() - t0 // includes a load, if one was needed
                if (job.stopped) return@withModel
                if (callback.start(model.sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) {
                    job.abandon()
                    return@withModel
                }
                sampleRate = model.sampleRate
                threads = model.threads
                val streamer = PcmStreamer(callback.maxBufferSize) { buffer, offset, length ->
                    callback.audioAvailable(buffer, offset, length) == TextToSpeech.SUCCESS
                }
                val sid = speakerIdFor(exposed, model)
                // Rates past what the model does cleanly, and every pitch
                // change, go through Sonic on the playback side (see Prosody).
                val stretcher = if (prosody.needsStretch) TempoStretcher(model.sampleRate, prosody) else null
                val readAhead = ReadAhead(maxAheadSamples = model.sampleRate * READ_AHEAD_SECONDS)
                job.attach(readAhead)
                result = readAhead.run(
                    // On ReadAhead's thread. EngineHost's lock stays with this
                    // thread, and run() returns only after that thread ended,
                    // so the model can't be released while it generates.
                    produce = { sink -> model.generate(text, sid, prosody.modelSpeed, exposed.language, opening, sink) },
                    // On this thread, at playback speed (audioAvailable blocks).
                    consume = { samples ->
                        if (job.stopped) {
                            false
                        } else {
                            val audio = stretcher?.process(samples) ?: samples
                            if (firstAudioMs < 0) firstAudioMs = SystemClock.elapsedRealtime() - t0
                            val accepted = streamer.write(audio)
                            if (!accepted) job.abandon() // client stopped or disconnected
                            accepted
                        }
                    },
                )
            }
            if (callback.hasStarted() && !callback.hasFinished()) callback.done()

            // One line per utterance so a logcat excerpt from a user's phone
            // tells us which app asked, which voice spoke, and how fast.
            // "generated ... in" counts only generating time (not waiting for
            // playback, which 0.2.2's "total" wrongly included).
            val r = result
            val audioMs = if (r != null && sampleRate > 0) r.samples * 1000 / sampleRate else 0L
            val stats = SpeechStats(caller, exposed.name, firstAudioMs, r?.synthMillis ?: 0L, audioMs, threads, job.stopped)
            graph.lastSpeech = stats
            // Per pack (all its speakers share one model); ignores short utterances.
            graph.speedBook.record(exposed.voice.id, stats.audioMs, stats.synthMs)
            val stopMs = job.stopRequestedAt.takeIf { it > 0L }?.let { SystemClock.elapsedRealtime() - it }
            Log.i(
                TAG,
                "Spoke ${text.length} chars for $caller with ${exposed.name} (${chosen.why}): " +
                    "model ready in ${modelReadyMs}ms, first audio ${firstAudioMs}ms, " +
                    "generated ${stats.audioMs}ms of audio in ${stats.synthMs}ms " +
                    "(${SpeechStats.formatFactor(stats.speedFactor)}x real time, $threads threads)" +
                    (if (prosody == Prosody.NORMAL) "" else ", ${prosody.describe()}") +
                    when {
                        stopMs != null -> ", stopped: ended ${stopMs}ms after the stop request"
                        job.stopped -> ", stopped by the client"
                        else -> ""
                    },
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Synthesis failed for $caller with ${exposed.name}", t)
            if (t is OutOfMemoryError) graph.engineHost.releaseIdle()
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Waits briefly for the first scan of the voices folder after a cold
     * start (normally already done). Bundled voices are listed even before
     * it, so this only matters for downloaded voices.
     *
     * Never on the main thread: the framework calls onLoadLanguage() from
     * onCreate() there, and blocking service creation for up to 1.5 s would
     * freeze whichever app is binding (QVoice's own screen included). The
     * answer then may miss a downloaded voice for a moment; the real
     * requests arrive on binder and synthesis threads, which do wait.
     */
    private fun awaitVoices() {
        if (Looper.myLooper() == Looper.getMainLooper()) return
        graph.voiceStore.awaitFirstScan(VOICE_SCAN_WAIT_MS)
    }

    private fun warmUp(voice: ExposedVoice) {
        graph.runInBackground {
            val prepared = graph.voiceStore.prepare(voice.voice)
            graph.engineHost.warmUp(prepared, variantFor(voice))
        }
    }

    private fun rebuildFrameworkVoices() {
        frameworkVoices = graph.exposedVoices.map { toFrameworkVoice(it) }
    }

    private fun toFrameworkVoice(v: ExposedVoice): Voice {
        // Offline availability is signalled by requiresNetworkConnection=false
        // below (the old "embeddedTts" feature string is deprecated since API 21).
        val features = HashSet<String>()
        // Not a framework constant — a hint the Riniso apps (and anyone else)
        // can use to offer "female/male voice" without parsing names.
        v.speaker?.gender?.takeIf { it != Gender.UNKNOWN }?.let { features += "qvoice.gender.${it.key}" }
        return Voice(
            v.name,
            TtsLocales.localeFor(v.languageTag),
            qualityOf(v.quality),
            latencyOf(v.voice.manifest.family),
            /* requiresNetworkConnection = */ false,
            features,
        )
    }

    private fun qualityOf(q: VoiceQuality): Int = when (q) {
        VoiceQuality.LOW -> Voice.QUALITY_LOW
        VoiceQuality.NORMAL -> Voice.QUALITY_NORMAL
        VoiceQuality.HIGH -> Voice.QUALITY_HIGH
        VoiceQuality.VERY_HIGH -> Voice.QUALITY_VERY_HIGH
    }

    /** Small models answer fast; the large neural families take noticeably longer to start. */
    private fun latencyOf(family: VoiceFamily): Int = when (family) {
        VoiceFamily.KITTEN, VoiceFamily.VITS -> Voice.LATENCY_NORMAL
        VoiceFamily.KOKORO, VoiceFamily.MATCHA, VoiceFamily.SUPERTONIC, VoiceFamily.POCKET -> Voice.LATENCY_HIGH
    }

    /**
     * Multi-language Kokoro is loaded once per language (sherpa-onnx fixes the
     * espeak voice and lexicons at load), keyed by the full tag because en-US
     * and en-GB differ in both. Every other family has a single load.
     */
    private fun variantFor(v: ExposedVoice): String? {
        val m = v.voice.manifest
        return if (m.family == VoiceFamily.KOKORO && m.languageConfig.isNotEmpty()) v.languageTag else null
    }

    /**
     * The speaker id, clamped to what the loaded model actually has. An
     * out-of-range id is a native crash inside sherpa-onnx, not an exception
     * (HayaiTTS hit this with catalogues that over-counted speakers).
     */
    private fun speakerIdFor(v: ExposedVoice, model: SpeechModel): Int {
        val sid = v.speaker?.sid ?: 0
        return if (model.numSpeakers > 0) sid.coerceIn(0, model.numSpeakers - 1) else 0
    }

    private fun callerOf(request: SynthesisRequest): String {
        val uid = request.callerUid
        return runCatching { packageManager.getPackagesForUid(uid)?.firstOrNull() }.getOrNull() ?: "uid $uid"
    }

    companion object {
        private const val TAG = "QVoice.Tts"
        private const val VOICE_SCAN_WAIT_MS = 1_500L

        /**
         * Request param (Boolean): this text was queued while another one
         * plays, so nobody waits for its first sound (D-055).
         */
        const val PARAM_QUEUED_AHEAD = "com.riniso.qvoice.QUEUED_AHEAD"

        /**
         * How far generation may run ahead of playback. Plenty to ride out a
         * busy moment on the phone; memory stays small (20 s at 44.1 kHz is
         * 3.5 MB of floats), and on Stop the work thrown away is bounded.
         */
        private const val READ_AHEAD_SECONDS = 20
    }
}
