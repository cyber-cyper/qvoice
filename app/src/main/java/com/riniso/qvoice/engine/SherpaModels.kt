package com.riniso.qvoice.engine

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.riniso.qvoice.voices.ApkAssets
import com.riniso.qvoice.voices.AssetSource
import com.riniso.qvoice.voices.InstalledVoice
import com.riniso.qvoice.voices.VoiceFamily
import java.io.File

/** [SpeechModel] backed by a sherpa-onnx [OfflineTts] instance. */
class SherpaSpeechModel(
    private val tts: OfflineTts,
    private val family: VoiceFamily,
    override val threads: Int,
) : SpeechModel {

    override val sampleRate: Int = tts.sampleRate()

    override val numSpeakers: Int = tts.numSpeakers()

    /**
     * Every family is fed one [TextChunker] chunk at a time (since slice 3):
     * that bounds how long the first sound and a Stop can take. Within a
     * chunk sherpa-onnx streams sentence by sentence (maxNumSentences = 1),
     * so those families get [TextChunker.STREAMING_MAX_CHARS], which only
     * cuts over-long sentences. Supertonic answers a request only when all of
     * it is done; its chunks (up to [TextChunker.DEFAULT_MAX_CHARS]) are
     * generated while the previous one plays (ReadAhead).
     *
     * sherpa-onnx contract: the callback returns 1 to continue, 0 to stop
     * after the current sentence (checked in the sandbox: returning 0 on the
     * 2nd of 5 sentences stops after 2). Each call also returns the whole
     * chunk as GeneratedAudio, which is ignored — it's already streamed.
     */
    override fun generate(text: String, sid: Int, speed: Float, language: String?, opening: Boolean, onChunk: (FloatArray) -> Boolean) {
        // Supertonic takes its language per request (GenerationConfig.extra
        // "lang", ISO-639-1); the other families fix it when loaded.
        val config = if (family == VoiceFamily.SUPERTONIC) {
            GenerationConfig(sid = sid, speed = speed, extra = mapOf("lang" to (language ?: "en")))
        } else {
            null
        }
        val maxChars = if (config != null) TextChunker.DEFAULT_MAX_CHARS else TextChunker.STREAMING_MAX_CHARS
        val chunks = if (opening) TextChunker.split(text, maxChars) else TextChunker.group(text, maxChars)
        for (chunk in chunks) {
            // Never a lambda here — see SherpaChunkCallback.
            val callback = SherpaChunkCallback(onChunk)
            if (config != null) {
                tts.generateWithConfigAndCallback(chunk, config, callback)
            } else {
                tts.generateWithCallback(chunk, sid, speed, callback)
            }
            if (callback.stopped) return
        }
    }

    override fun release() {
        tts.release()
    }
}

/**
 * The audio callback handed to sherpa-onnx: returns 1 to continue, 0 to stop.
 *
 * It must be a real class, never a lambda. sherpa-onnx's native code finds
 * the method by JNI signature — GetMethodID(cls, "invoke",
 * "([F)Ljava/lang/Integer;") — which a class implementing
 * `(FloatArray) -> Int` has (`Integer invoke(float[])`). Kotlin 2 compiles
 * lambdas with invokedynamic and D8 turns them into classes with only the
 * erased `invoke(Object)`; sherpa-onnx then logs "Failed to get the
 * callback", leaves a NoSuchMethodError pending, and ART aborts the whole
 * process on the next JNI call. That was the slice-2 crash on the first
 * "Speak" (Galaxy M31). EngineTest checks the signature;
 * proguard-rules.pro keeps it through R8; tools/check_project.py rejects a
 * lambda passed to generateWith*Callback.
 */
class SherpaChunkCallback(private val onChunk: (FloatArray) -> Boolean) : (FloatArray) -> Int {

    /** True once a chunk asked to stop (the client stopped or went away). */
    @Volatile
    var stopped: Boolean = false
        private set

    override fun invoke(samples: FloatArray): Int {
        val keepGoing = onChunk(samples)
        if (!keepGoing) stopped = true
        return if (keepGoing) 1 else 0
    }
}

/**
 * Loads voices with sherpa-onnx, using [SherpaConfigFactory] for the
 * configuration.
 *
 * @param assets the APK's assets: a built-in voice is read from there.
 * @param threads the CPU thread count to load with, read at every load (the
 *   user can change it; see QVoiceSettings.engineThreads). [signature]
 *   carries it, so EngineHost reloads a model cached with another count.
 */
class SherpaModelLoader(
    private val sharedEspeakDir: File,
    private val assets: ApkAssets,
    private val threads: () -> Int,
) : EngineHost.ModelLoader {

    override val signature: String get() = "threads=${threads()}"

    override fun load(voice: InstalledVoice, variant: String?): SpeechModel {
        val numThreads = threads()
        val config = SherpaConfigFactory.build(voice, sharedEspeakDir, numThreads, variant, assets)
        val tts = try {
            // With an AssetManager sherpa-onnx reads every model path from
            // the APK (a built-in voice); without one, from files (downloads).
            // The asset route has no error handling in sherpa-onnx 1.13.8 (a
            // missing asset ends the process), hence the checks in build().
            OfflineTts(assetManager = if (voice.assetDir != null) assets.manager else null, config = config)
        } catch (t: Throwable) {
            // Since sherpa-onnx 1.13.5 a failed native init throws here
            // instead of leaving a null pointer that crashes on first use.
            throw EngineLoadException("sherpa-onnx could not load ${voice.id}: ${t.message}", t)
        }
        return SherpaSpeechModel(tts, voice.manifest.family, numThreads)
    }
}

/**
 * Rough memory a voice needs once loaded, for [EngineHost]'s byte budget:
 * the size of the files sherpa-onnx reads into memory (model weights, voice
 * tables, lexicons). Activations add tens of MB on top; espeak-ng data is
 * shared and small. Missing files count as 0 — the load itself reports them.
 */
object ModelFootprint {
    /** @param assets the APK's assets, where a built-in voice's files are. */
    fun estimate(voice: InstalledVoice, assets: AssetSource? = null): Long {
        val f = voice.manifest.files
        val names = LinkedHashSet<String>()
        names += listOfNotNull(f.model, f.tokens, f.voices, f.vocoder)
        names += f.lexicons
        names += f.extra.values
        // Kokoro loads one language's lexicons per instance; counting all of
        // them overestimates by a few MB, which errs on the safe side.
        for (config in voice.manifest.languageConfig.values) names += config.lexicons.orEmpty()
        val assetDir = voice.assetDir
        return names.sumOf { name ->
            val size = if (assetDir != null) assets?.length("$assetDir/$name") ?: 0L else File(voice.dir, name).length()
            size.coerceAtLeast(0L)
        }
    }
}

/**
 * How many CPU threads a model gets. Phones are big.LITTLE: past the number
 * of fast cores (usually 2-4), more threads mostly add contention and heat.
 * Same starting point as HayaiTTS (2) with a step up on 8-core devices.
 */
object ThreadPolicy {

    /** The most the thread setting offers; beyond this ONNX Runtime gains nothing on a phone. */
    const val MAX_THREADS = 8

    fun defaultThreads(cores: Int = Runtime.getRuntime().availableProcessors()): Int = when {
        cores >= 8 -> 4
        cores >= 4 -> 2
        else -> 1
    }

    /**
     * The automatic choice: [defaultThreads], but never more threads than a
     * big.LITTLE processor has fast cores ([CpuInfo.fastCores]) — on the
     * common 2 fast + 6 slow design, 4 threads would put two on slow cores
     * and make every step wait for them. Galaxy M31 (4 + 4): 4, as before.
     */
    fun autoThreads(cores: Int, fastCores: Int?): Int {
        val base = defaultThreads(cores)
        return if (fastCores == null) base else minOf(base, fastCores).coerceAtLeast(1)
    }
}
