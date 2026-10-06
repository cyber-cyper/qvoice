package com.riniso.qvoice.engine

import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsPocketModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.riniso.qvoice.voices.AssetSource
import com.riniso.qvoice.voices.EspeakData
import com.riniso.qvoice.voices.InstalledVoice
import com.riniso.qvoice.voices.VoiceFamily
import java.io.File

/**
 * Turns an installed voice's manifest into a sherpa-onnx [OfflineTtsConfig].
 *
 * Only file paths are set explicitly; tuning knobs (noise scales, length
 * scale, silence scale) are left at the sherpa-onnx Kotlin defaults, which
 * are the values each model family was exported with (VITS 0.667 / 0.8 / 1.0,
 * Matcha 1.0 / 1.0). Overriding them per voice is a later, deliberate step.
 *
 * Every path comes from the manifest. Nothing is discovered by globbing the
 * folder, so a voice either has exactly the files its manifest names or the
 * load fails loudly here — instead of sherpa-onnx picking the wrong .onnx.
 *
 * A built-in voice ([InstalledVoice.assetDir]) gets paths inside the APK's
 * assets, which sherpa-onnx reads through the AssetManager (SherpaModelLoader
 * passes it). Only the eSpeak NG data is then a real folder: espeak-ng opens
 * its files by path.
 */
object SherpaConfigFactory {

    /**
     * Sentences per synthesis chunk. 1 = the first sentence is spoken while
     * the rest is still being generated, which is what screen readers and
     * read-aloud apps need; larger values only add latency.
     */
    const val SENTENCES_PER_CHUNK = 1

    class MissingFileException(message: String) : IllegalStateException(message)

    /**
     * @param kokoroLanguage for multi-language Kokoro models, the BCP-47 tag
     *   of the language being spoken ("en-GB"); its espeak-ng voice, lexicons,
     *   dictionary and text rules come from the manifest's languageConfig.
     *   sherpa-onnx fixes them per engine instance. Ignored by every other
     *   family.
     * @param assets the APK's assets, where a built-in voice's files are.
     */
    fun build(
        voice: InstalledVoice,
        sharedEspeakDir: File,
        numThreads: Int,
        kokoroLanguage: String? = null,
        assets: AssetSource? = null,
    ): OfflineTtsConfig {
        val m = voice.manifest
        val f = m.files
        val assetDir = voice.assetDir

        fun path(name: String): String {
            if (assetDir != null) {
                // Checked here, not left to sherpa-onnx: reading through the
                // AssetManager, it ends the whole process (exit -1) when an
                // asset is missing instead of failing the load.
                val asset = "$assetDir/$name"
                if (assets == null || !assets.exists(asset)) throw MissingFileException("${m.id}: missing $name in the APK")
                return asset
            }
            val file = File(voice.dir, name)
            if (!file.exists()) throw MissingFileException("${m.id}: missing ${file.name}")
            return file.absolutePath
        }

        /** Folders are opened by path (eSpeak NG data, the Chinese dictionary): an APK folder has none. */
        fun folder(name: String): String {
            check(assetDir == null) { "${m.id}: a voice read from the APK can't use its own $name folder" }
            return path(name)
        }

        fun required(name: String?, role: String): String =
            path(name ?: throw MissingFileException("${m.id}: manifest names no '$role' file"))

        fun extra(role: String): String = required(f.extra[role], role)

        val dataDir = when (m.espeakData) {
            EspeakData.SHARED -> {
                if (!File(sharedEspeakDir, "phontab").isFile) {
                    throw MissingFileException("shared espeak-ng data is not installed")
                }
                sharedEspeakDir.absolutePath
            }
            EspeakData.BUNDLED -> folder("espeak-ng-data")
            EspeakData.NONE -> ""
        }
        val langConfig = if (m.family == VoiceFamily.KOKORO) m.configFor(kokoroLanguage) else null
        val lexicon = (langConfig?.lexicons ?: f.lexicons).joinToString(",") { path(it) }
        val dictDir = (langConfig?.dictDir ?: f.dictDir)?.let { folder(it) } ?: ""
        val ruleFsts = (langConfig?.ruleFsts ?: f.ruleFsts).joinToString(",") { path(it) }

        val model = when (m.family) {
            VoiceFamily.KITTEN -> OfflineTtsModelConfig(
                kitten = OfflineTtsKittenModelConfig(
                    model = path(f.model),
                    voices = required(f.voices, "voices"),
                    tokens = required(f.tokens, "tokens"),
                    dataDir = dataDir,
                ),
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            )
            VoiceFamily.KOKORO -> OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = path(f.model),
                    voices = required(f.voices, "voices"),
                    tokens = required(f.tokens, "tokens"),
                    dataDir = dataDir,
                    lexicon = lexicon,
                    lang = langConfig?.espeakVoice.orEmpty(),
                    dictDir = dictDir,
                ),
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            )
            VoiceFamily.VITS -> OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = path(f.model),
                    lexicon = lexicon,
                    tokens = required(f.tokens, "tokens"),
                    dataDir = dataDir,
                    dictDir = dictDir,
                ),
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            )
            VoiceFamily.MATCHA -> OfflineTtsModelConfig(
                matcha = OfflineTtsMatchaModelConfig(
                    acousticModel = path(f.model),
                    vocoder = required(f.vocoder, "vocoder"),
                    lexicon = lexicon,
                    tokens = required(f.tokens, "tokens"),
                    dataDir = dataDir,
                    dictDir = dictDir,
                ),
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            )
            VoiceFamily.SUPERTONIC -> OfflineTtsModelConfig(
                supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = extra("duration_predictor"),
                    textEncoder = extra("text_encoder"),
                    vectorEstimator = extra("vector_estimator"),
                    vocoder = extra("vocoder"),
                    ttsJson = extra("tts_json"),
                    unicodeIndexer = extra("unicode_indexer"),
                    voiceStyle = extra("voice_style"),
                ),
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            )
            VoiceFamily.POCKET -> OfflineTtsModelConfig(
                pocket = OfflineTtsPocketModelConfig(
                    lmFlow = extra("lm_flow"),
                    lmMain = extra("lm_main"),
                    encoder = extra("encoder"),
                    decoder = extra("decoder"),
                    textConditioner = extra("text_conditioner"),
                    vocabJson = extra("vocab_json"),
                    tokenScoresJson = extra("token_scores_json"),
                ),
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            )
        }

        return OfflineTtsConfig(
            model = model,
            ruleFsts = ruleFsts,
            maxNumSentences = SENTENCES_PER_CHUNK,
        )
    }
}
