package com.riniso.qvoice.service

import com.riniso.qvoice.engine.SpeedEstimate
import com.riniso.qvoice.voices.InstalledVoice
import com.riniso.qvoice.voices.Speaker
import com.riniso.qvoice.voices.VoiceQuality

/**
 * Framework voice names:
 * - `<voiceId>` — single-speaker model;
 * - `<voiceId>#<speakerKey>` — a speaker offered in one language;
 * - `<voiceId>#<speakerKey>@<languageTag>` — a speaker offered in several
 *   languages (Supertonic's speakers speak 31), one name per language.
 *
 * These names are public API: client apps (including the Riniso sister apps)
 * may store them and pass them back through TextToSpeech.setVoice(). Voice ids
 * and speaker keys are therefore never renamed once shipped, and neither can
 * contain '#' or '@' (VoiceManifestJson.isSafeId). The one rename so far —
 * the built-in voice, before the first public release — is kept working by
 * [RENAMED]: "kitten-nano-en-v0_8-int8#rosie" still finds Rosie.
 */
object VoiceNames {
    const val SPEAKER_SEPARATOR = '#'
    const val LANGUAGE_SEPARATOR = '@'

    /**
     * Old voice id -> current id. Slices 1-2 named the built-in voice after
     * its int8 build; 0.3.0 ships the fp32 build under the build-neutral id
     * "kitten-nano-en" (BundledVoices.KITTEN_NANO_EN).
     */
    private val RENAMED = mapOf("kitten-nano-en-v0_8-int8" to "kitten-nano-en")

    data class Parts(val voiceId: String, val speakerKey: String?, val languageTag: String?)

    fun of(voiceId: String, speakerKey: String?, languageTag: String? = null): String = buildString {
        append(voiceId)
        if (!speakerKey.isNullOrEmpty()) append(SPEAKER_SEPARATOR).append(speakerKey)
        if (!languageTag.isNullOrEmpty()) append(LANGUAGE_SEPARATOR).append(languageTag)
    }

    /** Null for a blank or malformed name. */
    fun parse(name: String?): Parts? {
        var n = name?.trim().orEmpty()
        if (n.isEmpty()) return null
        var language: String? = null
        val at = n.lastIndexOf(LANGUAGE_SEPARATOR)
        if (at >= 0) {
            language = n.substring(at + 1).ifEmpty { null }
            n = n.substring(0, at)
        }
        val cut = n.lastIndexOf(SPEAKER_SEPARATOR)
        if (cut < 0) return if (n.isEmpty()) null else Parts(current(n), null, language)
        val id = n.substring(0, cut)
        if (id.isEmpty()) return null
        return Parts(current(id), n.substring(cut + 1).ifEmpty { null }, language)
    }

    private fun current(voiceId: String): String = RENAMED[voiceId] ?: voiceId
}

/** One entry of the voice list QVoice shows the framework: a model, one speaker, one language. */
data class ExposedVoice(
    val name: String,
    val voice: InstalledVoice,
    val speaker: Speaker?,
    /** BCP-47, e.g. "en-US". */
    val languageTag: String,
    /** ISO-639-1 (or -3 when no 2-letter code exists), lowercase. */
    val language: String,
    /** ISO-3166 alpha-2, uppercase, or null. */
    val region: String?,
    /** The speaker's quality if it overrides the model's, else the model's. */
    val quality: VoiceQuality,
)

/**
 * Expands installed voices into framework voices: one per speaker per
 * language the speaker is offered in. Within a model the default speaker
 * comes first, so "first voice for a language" is always a sensible pick.
 */
fun exposeVoices(installed: List<InstalledVoice>): List<ExposedVoice> {
    val out = ArrayList<ExposedVoice>()
    for (voice in installed) {
        val m = voice.manifest
        val speakers: List<Speaker?> = if (m.speakers.isEmpty()) {
            listOf(null)
        } else {
            val first = m.defaultSpeaker
            listOfNotNull(first) + m.speakers.filter { it !== first }
        }
        for (speaker in speakers) {
            val tags = m.languagesOf(speaker)
            val multi = tags.size > 1
            for (tag in tags) {
                val (language, region) = TtsLocales.parseTag(tag) ?: continue
                val normalised = TtsLocales.tagOf(language, region)
                out += ExposedVoice(
                    name = VoiceNames.of(m.id, speaker?.key, if (multi) normalised else null),
                    voice = voice,
                    speaker = speaker,
                    languageTag = normalised,
                    language = language,
                    region = region,
                    quality = m.qualityOf(speaker),
                )
            }
        }
    }
    return out
}

/** Where the per-language default voice choices live (SharedPreferences in the app). */
interface VoiceDefaults {
    /** @param languageKey "ta" or "ta-IN". */
    fun defaultVoiceFor(languageKey: String): String?
}

/**
 * Decides which voice speaks what. Pure logic (no Android types) so every
 * rule below is unit-tested.
 *
 * Precedence for a synthesis request:
 * 1. The voice the client asked for by name, if it exists.
 * 2. The default for the request's language (user choice, else the best
 *    voice that keeps up on this phone — see [defaultFor]).
 * 3. If QVoice has no voice for that language: the English default, but only
 *    when the text is in a script English can read (see [ScriptDetector]);
 *    otherwise refuse, so the client can fall back to another engine
 *    instead of hearing gibberish.
 */
class VoiceSelector(
    private val voices: () -> List<ExposedVoice>,
    private val defaults: VoiceDefaults,
    /** Times faster than real time a voice (by id) runs on this phone; null = unknown. */
    private val speedOf: (String) -> Float? = { null },
) {
    enum class Availability { NOT_SUPPORTED, LANGUAGE, LANGUAGE_AND_REGION }

    sealed class Resolution {
        data class Speak(val voice: ExposedVoice, val why: String) : Resolution()
        data class Refuse(val why: String) : Resolution()
    }

    fun findByName(name: String?): ExposedVoice? {
        val parts = VoiceNames.parse(name) ?: return null
        val candidates = voices().filter { v ->
            v.voice.id == parts.voiceId && (parts.speakerKey == null || v.speaker?.key == parts.speakerKey)
        }
        // A bare model id selects the default speaker; a name without a
        // language selects that speaker's first language.
        if (parts.languageTag == null) return candidates.firstOrNull()
        val wanted = TtsLocales.parseTag(parts.languageTag) ?: return null
        return candidates.firstOrNull { it.language == wanted.first && it.region == wanted.second }
            ?: candidates.firstOrNull { it.language == wanted.first }
    }

    /** @param language / [region] may be alpha-2 or alpha-3 in any case. */
    fun availability(language: String?, region: String?): Availability {
        val lang = TtsLocales.toIso2Language(language) ?: return Availability.NOT_SUPPORTED
        val reg = TtsLocales.toIso2Region(region)
        val matches = voices().filter { it.language == lang }
        return when {
            matches.isEmpty() -> Availability.NOT_SUPPORTED
            reg != null && matches.any { it.region == reg } -> Availability.LANGUAGE_AND_REGION
            else -> Availability.LANGUAGE
        }
    }

    /**
     * The voice for a language: the user's saved choice for "lang-REGION",
     * then for "lang", then the best installed voice: one that keeps up on
     * this phone first (unknown speed counts as keeping up), then region
     * match, then higher quality, then downloaded before bundled, then list
     * order.
     *
     * Speed ranks first because a voice slower than real time pauses after
     * every sentence — on a Galaxy M31, Kokoro (the highest quality) ran at a
     * quarter of real time, and as the automatic default it would have made
     * every app that reads aloud stutter. A slow voice still speaks when it's
     * the only one for the language, or when the user picked it.
     */
    fun defaultFor(language: String?, region: String?): ExposedVoice? {
        val lang = TtsLocales.toIso2Language(language) ?: return null
        val reg = TtsLocales.toIso2Region(region)
        if (reg != null) {
            findByName(defaults.defaultVoiceFor(TtsLocales.tagOf(lang, reg)))
                ?.takeIf { it.language == lang }?.let { return it }
        }
        findByName(defaults.defaultVoiceFor(lang))?.takeIf { it.language == lang }?.let { return it }

        val candidates = voices().withIndex().filter { it.value.language == lang }
        if (candidates.isEmpty()) return null
        return candidates.sortedWith(
            compareByDescending<IndexedValue<ExposedVoice>> { keepsUp(it.value) }
                .thenByDescending { reg != null && it.value.region == reg }
                .thenByDescending { it.value.quality.ordinal }
                .thenBy { if (it.value.voice.bundled) 1 else 0 }
                .thenBy { it.index },
        ).first().value
    }

    private fun keepsUp(v: ExposedVoice): Boolean =
        speedOf(v.voice.id)?.let { it >= SpeedEstimate.KEEPS_UP_AT } ?: true

    fun resolve(voiceName: String?, language: String?, region: String?, text: CharSequence): Resolution {
        findByName(voiceName)?.let { return Resolution.Speak(it, "requested voice") }

        val lang = TtsLocales.toIso2Language(language)
        if (lang != null) {
            defaultFor(lang, region)?.let { return Resolution.Speak(it, "default for $lang") }
        }

        val fallback = defaultFor(FALLBACK_LANGUAGE, null) ?: voices().firstOrNull()
            ?: return Resolution.Refuse("no voices installed")
        if (!ScriptDetector.canRead(fallback.language, text)) {
            return Resolution.Refuse(
                "no voice for '${language ?: "?"}' and the text is not in ${fallback.language}'s script",
            )
        }
        return Resolution.Speak(fallback, "fallback (no voice for '${language ?: "?"}')")
    }

    companion object {
        const val FALLBACK_LANGUAGE = "en"
    }
}
