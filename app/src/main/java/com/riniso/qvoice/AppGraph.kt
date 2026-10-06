package com.riniso.qvoice

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.riniso.qvoice.catalog.Catalog
import com.riniso.qvoice.catalog.CatalogJson
import com.riniso.qvoice.download.InstallJobService
import com.riniso.qvoice.download.SharedPrefsLibraryPrefs
import com.riniso.qvoice.download.SystemDownloads
import com.riniso.qvoice.download.VoiceLibrary
import com.riniso.qvoice.engine.CpuInfo
import com.riniso.qvoice.engine.EngineHost
import com.riniso.qvoice.engine.ModelFootprint
import com.riniso.qvoice.engine.SherpaModelLoader
import com.riniso.qvoice.engine.SpeedBook
import com.riniso.qvoice.engine.SpeedEstimate
import com.riniso.qvoice.engine.ThreadPolicy
import com.riniso.qvoice.reader.FileMemoryStore
import com.riniso.qvoice.reader.NowPlaying
import com.riniso.qvoice.reader.ReadAloud
import com.riniso.qvoice.reader.ReadAloudService
import com.riniso.qvoice.reader.ReaderLanguage
import com.riniso.qvoice.reader.ReaderMemory
import com.riniso.qvoice.reader.SystemPlaybackGuard
import com.riniso.qvoice.reader.TtsSpeaker
import com.riniso.qvoice.service.ExposedVoice
import com.riniso.qvoice.service.SpeechStats
import com.riniso.qvoice.service.VoiceSelector
import com.riniso.qvoice.service.exposeVoices
import com.riniso.qvoice.settings.QVoiceSettings
import com.riniso.qvoice.voices.ApkAssets
import com.riniso.qvoice.voices.ArchiveInstaller
import com.riniso.qvoice.voices.BundledVoice
import com.riniso.qvoice.voices.BundledVoices
import com.riniso.qvoice.voices.QVoicePaths
import com.riniso.qvoice.voices.VoiceStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The app's object graph, built once per process in [QVoiceApp].
 *
 * Plain constructor wiring instead of Hilt/Koin: the graph is a handful of
 * objects, and annotation processing is the part of a build most likely to
 * break with a Kotlin/AGP upgrade. The TTS service, the activities and the
 * UI all reach it through `(application as QVoiceApp).graph`.
 */
class AppGraph(context: Context) {

    private val appContext: Context = context.applicationContext

    val paths = QVoicePaths(appContext.filesDir)

    val settings = QVoiceSettings(appContext)

    /** The APK's assets: the built-in voice is read from there, the eSpeak NG data copied from there. */
    private val assets = ApkAssets(appContext.assets)

    val bundledVoices = BundledVoices(assets, paths)

    val voiceStore = VoiceStore(paths, bundledVoices)

    /** The processor layout, read on first use (the start-up thread logs it). */
    val cpu: CpuInfo by lazy { CpuInfo.read() }

    /** The thread count "automatic" means on this phone. */
    val autoThreads: Int by lazy { ThreadPolicy.autoThreads(Runtime.getRuntime().availableProcessors(), cpu.fastCores) }

    /** What the voices load with: the user's choice (Try it → CPU threads), else [autoThreads]. */
    fun engineThreads(): Int = settings.engineThreads.takeIf { it > 0 } ?: autoThreads

    val engineHost = EngineHost(
        loader = SherpaModelLoader(paths.espeakDataDir, assets) { engineThreads() },
        maxBytes = EngineHost.budgetFor(totalRamBytes(appContext)),
        sizeOf = { voice -> ModelFootprint.estimate(voice, assets) },
    )

    /**
     * The last utterance's numbers, written by the engine service. The UI is
     * in the same process and shows them under "Try it".
     */
    @Volatile
    var lastSpeech: SpeechStats? = null

    /** The framework-facing voice list, rebuilt whenever [voiceStore] changes. */
    @Volatile
    var exposedVoices: List<ExposedVoice> = emptyList()
        private set

    /** How fast each voice runs on this phone, learnt from real use. */
    val speedBook = SpeedBook(settings)

    /**
     * [voiceId]'s speed on this phone: measured if it has spoken here, else
     * predicted from the built-in voice's measured speed and the catalogue's
     * relative-speed hint; null if neither is known yet.
     */
    fun speedEstimate(voiceId: String): SpeedEstimate? =
        SpeedEstimate.of(voiceId, BundledVoices.KITTEN_NANO_EN.id, speedBook::measured) { id -> catalog.find(id)?.speedHint }

    // Speed-aware: the automatic default for a language skips voices too slow
    // for this phone when one that keeps up exists (a user's choice still wins).
    val selector = VoiceSelector({ exposedVoices }, settings) { id -> speedEstimate(id)?.factor }

    /**
     * The downloadable voices (assets/catalog.json). Parsed on first use; a
     * broken file leaves the library empty rather than crashing the engine.
     */
    val catalog: Catalog by lazy {
        try {
            appContext.assets.open(CatalogJson.ASSET).bufferedReader().use { CatalogJson.parse(it.readText()) }
        } catch (e: Exception) {
            Log.e(TAG, "Voice catalogue unreadable", e)
            Catalog.EMPTY
        }
    }

    /** For main-thread work that lasts as long as the app (see [readAloud]). */
    private val mainScope = MainScope()

    /**
     * Read aloud (the reader screen, "Read aloud" in text selections, Share):
     * app-wide so reading survives the screen being recreated, closed or off.
     * Created, and used, on the main thread (see ReadAloud).
     */
    val readAloud: ReadAloud by lazy {
        ReadAloud(
            speaker = TtsSpeaker(appContext),
            guard = SystemPlaybackGuard(appContext),
            localeFor = { paragraph ->
                ReaderLanguage.pick(paragraph, Locale.getDefault(), exposedVoices.mapTo(HashSet()) { it.language })
            },
            initialRate = settings.readerRate,
            onRateChanged = { settings.readerRate = it },
            screenReaderOn = {
                appContext.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
            },
            // Counts through deep sleep and never jumps with a clock change.
            clock = SystemClock::elapsedRealtime,
        ).also { reader ->
            // The last text and place, back after QVoice was closed or the
            // phone restarted (D-050). Read here, on the main thread, once per
            // process: a few hundred kilobytes at most, and the reader then
            // never shows empty first and jumps a moment later. The engine
            // never pays for it: the TTS service doesn't touch the reader.
            val memory = readerMemory
            memory.restore()?.let { reader.restore(it.paragraphs, it.index, it.sentence, it.truncated) }
            // Reading on with the screen off or in another app needs the media
            // service: started when reading starts, it then follows the reader
            // by itself until the listening session ends. Dispatchers.Main
            // (never .immediate) delivers the state after the reader's own
            // call has returned, and only its latest value.
            mainScope.launch {
                reader.state.collect { if (NowPlaying.startsService(it)) ReadAloudService.startIfNeeded(appContext) }
            }
            mainScope.launch { reader.state.collect(memory::onState) }
        }
    }

    /**
     * Where the reader's text is remembered: QVoice's no-backup folder, so it
     * stays on this phone (never in a backup or a transfer to a new phone).
     * Its own thread, because the background executor below can be busy with
     * a model warm-up for seconds.
     */
    private val readerMemory: ReaderMemory by lazy {
        val writer = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "qvoice-memory").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
        ReaderMemory(FileMemoryStore(File(File(appContext.noBackupFilesDir, "reader"), "last.txt"), writer))
    }

    /**
     * Lazy so the catalogue is first parsed off the main thread (by the
     * start-up refresh below), not in Application.onCreate.
     */
    val library: VoiceLibrary by lazy {
        VoiceLibrary(
            catalog = { catalog },
            store = voiceStore,
            downloads = SystemDownloads(appContext),
            prefs = SharedPrefsLibraryPrefs(appContext),
            installer = ArchiveInstaller(paths, prepareSharedData = { bundledVoices.ensureInstalled() }),
            engine = engineHost,
            paths = paths,
            scheduleInstall = { voiceId -> InstallJobService.schedule(appContext, voiceId) },
        )
    }

    /**
     * One low-priority thread for all background work (the first-run eSpeak
     * NG data copy, model warm-ups, the speed check). A single thread means
     * warm-ups never pile up on each other.
     */
    private val background: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "qvoice-bg").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    init {
        voiceStore.addListener { voices -> exposedVoices = exposeVoices(voices) }
        voiceStore.seedDeclared()
        background.execute {
            // Each step on its own: a failure is logged and the next step still
            // runs. An exception escaping this thread would reach Android's
            // default handler, which kills the whole process — engine included.
            // One line that makes a pasted log say which processor it came from.
            startupStep("processor check") {
                Log.i(TAG, "Processor: ${cpu.describe()}; automatic thread count $autoThreads")
            }
            startupStep("staging cleanup") { paths.clearStaging() }
            startupStep("voice scan") { voiceStore.refresh() }
            // Retried on first use if it fails (VoiceStore.prepare).
            startupStep("built-in voice setup") {
                val freed = bundledVoices.ensureInstalled()
                if (freed > 0) Log.i(TAG, "Deleted old copies of built-in voices: ${freed / MIB} MB freed")
            }
            startupStep("built-in voice check") { bundledVoices.declared.forEach(::logBuiltInVoice) }
            startupStep("voice rescan") {
                voiceStore.refresh()
                val problems = voiceStore.lastScanProblems
                if (problems.isNotEmpty()) Log.w(TAG, "Skipped voice folders: $problems")
            }
            // Downloads that finished (or failed) while QVoice wasn't running.
            startupStep("download check") { library.refresh() }
        }
    }

    /**
     * One line per built-in voice, confirming its model is read from the APK
     * and stored uncompressed there (a compressed one would be inflated in
     * full on every load: see ApkAssets.isUncompressed).
     */
    private fun logBuiltInVoice(voice: BundledVoice) {
        val id = voice.manifest.id
        val model = "${voice.assetDir}/${voice.manifest.files.model}"
        when {
            !assets.exists(model) -> Log.e(TAG, "Built-in voice $id: $model is missing from the APK")
            assets.isUncompressed(model) ->
                Log.i(TAG, "Built-in voice $id: read from the APK (${assets.length(model) / MIB} MB model, stored uncompressed)")
            else -> Log.w(TAG, "Built-in voice $id: model stored compressed in the APK, so every load inflates it; check noCompress in app/build.gradle.kts")
        }
    }

    private inline fun startupStep(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(TAG, "Start-up $what failed", t)
        }
    }

    /**
     * Measures the built-in voice once, silently, so the voice library can
     * predict every other voice's speed before the user has spoken with it
     * (a fresh install that opens the library first). Skipped once a real
     * utterance has measured it; takes a few seconds on the background thread.
     */
    fun checkSpeedIfNeeded() {
        val builtIn = BundledVoices.KITTEN_NANO_EN.id
        if (speedBook.measured(builtIn) != null) return
        runInBackground {
            if (speedBook.measured(builtIn) != null) return@runInBackground
            val voice = voiceStore.prepare(voiceStore.find(builtIn) ?: return@runInBackground)
            val sid = voice.manifest.defaultSpeaker?.sid ?: 0
            engineHost.withModel(voice, null) { model ->
                var samples = 0L
                val start = System.nanoTime()
                model.generate(SPEED_CHECK_TEXT, sid, 1f, "en") { chunk ->
                    samples += chunk.size
                    true
                }
                val synthMs = (System.nanoTime() - start) / 1_000_000
                speedBook.record(builtIn, samples * 1000 / model.sampleRate, synthMs)
                Log.i(TAG, "Speed check: ${voice.id} ${samples * 1000 / model.sampleRate} ms of audio in $synthMs ms")
            }
        }
    }

    fun runInBackground(task: () -> Unit) {
        background.execute {
            try {
                task()
            } catch (t: Throwable) {
                Log.w(TAG, "Background task failed", t)
            }
        }
    }

    companion object {
        const val TAG = "QVoice"

        private const val MIB = 1024L * 1024L

        /** Two ordinary sentences (~4 s of speech): long enough to measure, short enough to be quick. */
        private const val SPEED_CHECK_TEXT = "Good morning. Your next appointment is at half past ten, with Doctor Rao."

        private fun totalRamBytes(context: Context): Long {
            val am = context.getSystemService(ActivityManager::class.java) ?: return 0L
            return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
        }
    }
}
