package com.riniso.qvoice.voices

import java.io.File

/**
 * Model families the sherpa-onnx runtime can run. [key] is the spelling used
 * in voice manifests (qvoice-voice.json) and must never change once shipped.
 *
 * Piper voices are VITS models as far as sherpa-onnx is concerned, so they use
 * [VITS]; "piper" is a catalog label, not a runtime family.
 */
enum class VoiceFamily(val key: String) {
    KITTEN("kitten"),
    KOKORO("kokoro"),
    VITS("vits"),
    MATCHA("matcha"),
    SUPERTONIC("supertonic"),
    POCKET("pocket");

    companion object {
        fun fromKey(key: String?): VoiceFamily? = entries.firstOrNull { it.key == key }
    }
}

enum class Gender(val key: String) {
    FEMALE("female"),
    MALE("male"),
    UNKNOWN("unknown");

    companion object {
        fun fromKey(key: String?): Gender = entries.firstOrNull { it.key == key } ?: UNKNOWN
    }
}

/**
 * How good a voice sounds, mapped onto android.speech.tts.Voice.QUALITY_* by
 * the service. Apps that pick "the best voice for a locale" sort on it, so
 * it is set per voice in the manifest rather than guessed from file size.
 */
enum class VoiceQuality(val key: String) {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high"),
    VERY_HIGH("very_high");

    companion object {
        fun fromKey(key: String?): VoiceQuality = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

/**
 * Where a voice's espeak-ng pronunciation data lives.
 *
 * Every sherpa-onnx Piper/Kitten/Kokoro bundle carries its own 19 MB copy of
 * espeak-ng-data, and all of them are byte-identical (checked across four
 * bundles from the same release). [SHARED] points the voice at the one copy
 * QVoice keeps in `files/shared/espeak-ng-data`; [BUNDLED] uses the copy
 * inside the voice folder; [NONE] is for families that never phonemize with
 * espeak (Pocket, Supertonic).
 */
enum class EspeakData(val key: String) {
    SHARED("shared"),
    BUNDLED("bundled"),
    NONE("none");

    companion object {
        fun fromKey(key: String?): EspeakData = entries.firstOrNull { it.key == key } ?: BUNDLED
    }
}

/**
 * One selectable voice inside a model file. Multi-speaker models (Kitten has
 * 8, Kokoro 50+) expose each speaker as its own framework voice.
 *
 * @property sid the speaker id sherpa-onnx expects.
 * @property key stable lowercase identifier used in framework voice names
 *   ("<voiceId>#<key>"). Apps may persist those names, so a key must never be
 *   renamed once shipped.
 * @property languages BCP-47 tags this speaker is offered in. Empty = every
 *   language of the model. Kokoro gives each speaker its own single language
 *   (af_heart is en-US, hf_alpha is hi-IN); Supertonic's speakers speak all
 *   31 of the model's languages, so they leave it empty.
 * @property quality overrides the model's quality for this speaker — Kokoro's
 *   author grades its Hindi voices C while its English ones are A-grade, and
 *   "best voice for a language" must not pick a weak speaker over a better
 *   model. Null = the model's quality.
 */
data class Speaker(
    val sid: Int,
    val key: String,
    val displayName: String,
    val gender: Gender,
    val languages: List<String> = emptyList(),
    val quality: VoiceQuality? = null,
)

/**
 * Per-language load settings for multi-language models (Kokoro): which espeak-ng
 * voice phonemizes the text, which pronunciation lexicons to load, and the
 * Chinese-only word-segmentation dictionary and text-normalisation rules.
 * sherpa-onnx fixes all of them when the model is loaded, so each language is
 * its own load variant. Null fields fall back to the manifest's [VoiceFiles].
 *
 * espeak-ng voice names are its file names, not BCP-47: British English is
 * "en" (there is no "en-gb" voice — asking for one yields silence), American is
 * "en-us", Brazilian Portuguese "pt-br", Mandarin "cmn".
 *
 * Why the rules are per language: Kokoro's rule FSTs (number-zh.fst etc.)
 * rewrite digits as Chinese numerals. Applied to English, "3:45" came out as
 * "san si wu" (checked in the sandbox); without them, Chinese text drops its
 * digits. So only the Chinese variant gets them.
 */
data class LanguageConfig(
    val espeakVoice: String? = null,
    val lexicons: List<String>? = null,
    val dictDir: String? = null,
    val ruleFsts: List<String>? = null,
)

/**
 * File names inside the voice folder, per role. The manifest names every file
 * explicitly: the runtime never guesses by globbing for "*.onnx", because
 * upstream bundles name the same role differently (model.int8.onnx,
 * model.fp32.onnx, en_US-amy-medium.onnx, ...).
 *
 * [extra] carries family-specific roles (Pocket: lm_flow, lm_main, encoder,
 * decoder, text_conditioner, vocab_json, token_scores_json; Supertonic:
 * duration_predictor, text_encoder, vector_estimator, vocoder, tts_json,
 * unicode_indexer, voice_style).
 */
data class VoiceFiles(
    val model: String,
    val tokens: String? = null,
    val voices: String? = null,
    val lexicons: List<String> = emptyList(),
    val vocoder: String? = null,
    val dictDir: String? = null,
    val ruleFsts: List<String> = emptyList(),
    val extra: Map<String, String> = emptyMap(),
)

data class LicenseInfo(
    val name: String,
    val holder: String,
    val url: String,
)

/**
 * Everything QVoice knows about an installed voice. Written as
 * `qvoice-voice.json` inside the voice folder, LAST, when an install
 * completes — so a folder without a manifest is an interrupted install and is
 * ignored (and cleaned up) rather than half-loaded.
 */
data class VoiceManifest(
    val schema: Int,
    val id: String,
    val family: VoiceFamily,
    val displayName: String,
    val version: Int,
    val sampleRate: Int,
    val languages: List<String>,
    val quality: VoiceQuality,
    val files: VoiceFiles,
    val espeakData: EspeakData,
    val speakers: List<Speaker>,
    val defaultSpeakerKey: String?,
    val license: LicenseInfo,
    val sourceUrl: String? = null,
    val sourceSha256: String? = null,
    /** Keyed by BCP-47 tag ("en-GB") or bare language ("hi"); see [configFor]. */
    val languageConfig: Map<String, LanguageConfig> = emptyMap(),
) {
    /** The speaker used when a request names the voice but not a speaker. */
    val defaultSpeaker: Speaker?
        get() = speakers.firstOrNull { it.key == defaultSpeakerKey } ?: speakers.firstOrNull()

    /** Languages a speaker is offered in: its own list, else every model language. */
    fun languagesOf(speaker: Speaker?): List<String> =
        speaker?.languages?.takeIf { it.isNotEmpty() } ?: languages

    fun qualityOf(speaker: Speaker?): VoiceQuality = speaker?.quality ?: quality

    /** Load settings for a language: exact tag first ("en-GB"), then the bare language ("en"). */
    fun configFor(languageTag: String?): LanguageConfig? {
        if (languageTag.isNullOrEmpty()) return null
        return languageConfig[languageTag] ?: languageConfig[languageTag.substringBefore('-')]
    }

    /**
     * Every file or folder (dictDir) the engine may open for this voice,
     * relative to the voice folder. An install is complete only when all of
     * them exist; checking before the swap turns a truncated archive into an
     * install error instead of a native load failure later.
     */
    fun requiredFiles(): List<String> {
        val out = LinkedHashSet<String>()
        out += files.model
        out += listOfNotNull(files.tokens, files.voices, files.vocoder, files.dictDir)
        out += files.lexicons
        out += files.ruleFsts
        out += files.extra.values
        for (config in languageConfig.values) {
            out += config.lexicons.orEmpty()
            out += listOfNotNull(config.dictDir)
            out += config.ruleFsts.orEmpty()
        }
        return out.toList()
    }

    companion object {
        const val CURRENT_SCHEMA = 1
        const val FILE_NAME = "qvoice-voice.json"
    }
}

/**
 * A voice the engine can use.
 *
 * @property dir the voice folder of a downloaded voice. For a built-in voice
 *   it only names where versions before 1.0.0 kept a copy (deleted on
 *   start-up); nothing is read there — its files are at [assetDir].
 * @property ready usable right now: always for a downloaded voice (its
 *   manifest is on disk); for a built-in voice once the shared eSpeak NG data
 *   has been copied out of the APK (the one part that must be a real folder).
 *   A built-in voice is advertised before that so the framework can select it
 *   immediately; the first synthesis does the copy.
 * @property assetDir for a voice shipped inside the APK: the folder under
 *   `assets/` holding its files. sherpa-onnx reads them from the APK through
 *   the AssetManager, so they are never copied (since 1.0.0; the copy cost
 *   60 MB of every phone's storage). Null for downloaded voices.
 */
data class InstalledVoice(
    val manifest: VoiceManifest,
    val dir: File,
    val ready: Boolean,
    val assetDir: String? = null,
) {
    val id: String get() = manifest.id

    /**
     * Shipped inside the APK: always available, never deleted. Derived, not
     * stored, so it can't disagree with where the files are read from.
     */
    val bundled: Boolean get() = assetDir != null
}
