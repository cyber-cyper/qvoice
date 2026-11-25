// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Riniso. Part of "RinisoSpeech", the shared speech helper of
// the Riniso apps. Keep this file IDENTICAL in every app (copy, don't edit);
// the canonical copy lives in the QVoice repository under integration/.
package com.riniso.speech

/**
 * The rules of RinisoSpeech with no Android types, so every one of them is
 * unit-tested (SpeechRoutingTest). RinisoSpeech.kt applies them.
 */
object SpeechRouting {

    /** Which engine speaks a request. */
    enum class Engine { QVOICE, SYSTEM }

    /**
     * The engine for a request:
     * - a voice the user chose ([pinned], from a [VoiceRef]) decides, except
     *   that a QVoice voice falls back to the system engine when QVoice is
     *   gone (uninstalled, disabled);
     * - "Phone's default voice": the system engine, always;
     * - QVoice not installed: the system engine;
     * - QVoice has no voice for the language ([qvoiceHasLanguage] false, e.g.
     *   Tamil): the system engine;
     * - otherwise QVoice. Unknown support (null, QVoice still starting) is
     *   tried with QVoice: it refuses text it can't read, and RinisoSpeech
     *   then repeats the request on the system engine.
     */
    fun engineFor(choice: EngineChoice, qvoiceInstalled: Boolean, qvoiceHasLanguage: Boolean?, pinned: Engine? = null): Engine = when {
        pinned == Engine.SYSTEM -> Engine.SYSTEM
        pinned == Engine.QVOICE -> if (qvoiceInstalled) Engine.QVOICE else Engine.SYSTEM
        choice == EngineChoice.SYSTEM -> Engine.SYSTEM
        !qvoiceInstalled -> Engine.SYSTEM
        qvoiceHasLanguage == false -> Engine.SYSTEM
        else -> Engine.QVOICE
    }

    /** What RinisoSpeech needs of an android.speech.tts.Voice. */
    data class VoiceInfo(
        val name: String,
        /** ISO-639 language, as Locale.getLanguage() gives it ("en", "ta"). */
        val language: String,
        /** ISO-3166 country or "" ("US", "IN"). */
        val country: String,
        val features: Set<String>,
        /** Voice.LATENCY_* (100 very low ... 500 very high). */
        val latency: Int,
        val requiresNetwork: Boolean,
        val notInstalled: Boolean,
    )

    /** Voice.LATENCY_NORMAL: QVoice's fast voices (built-in, Piper); Kokoro and Supertonic report HIGH. */
    const val LATENCY_NORMAL = 300

    /** QVoice marks each voice "qvoice.gender.female" or "qvoice.gender.male" (when known). */
    const val GENDER_FEATURE_PREFIX = "qvoice.gender."

    /**
     * QVoice's built-in voice: always installed, so an engine that lists no
     * voice of it isn't QVoice (Android binds the default engine instead
     * when the requested one fails to start, without saying so).
     */
    const val QVOICE_BUILT_IN_MODEL = "kitten-nano-en"

    fun isQVoice(voices: List<VoiceInfo>): Boolean = voices.any { modelOf(it.name) == QVOICE_BUILT_IN_MODEL }

    fun genderOf(v: VoiceInfo): VoiceGender = when {
        GENDER_FEATURE_PREFIX + "female" in v.features -> VoiceGender.FEMALE
        GENDER_FEATURE_PREFIX + "male" in v.features -> VoiceGender.MALE
        else -> VoiceGender.ANY
    }

    /** Starts at once (built-in, Piper, most phone voices), as opposed to Kokoro or Supertonic. */
    fun isFast(v: VoiceInfo): Boolean = v.latency <= LATENCY_NORMAL

    /**
     * The voice to set for a request, or null to let the engine use its own
     * default for the language (QVoice's default is the user's choice in
     * QVoice, else the best voice that keeps up on this phone: usually the
     * right answer, so null is the normal case).
     *
     * 1. [preferredName], if the engine still has it (a voice the user picked
     *    in the app's settings), in any language: a chosen voice is the
     *    user's word.
     * 2. With a [gender] wish: a voice of that gender for the language, from
     *    the same model as the engine's default when there is one (so the
     *    sound stays the same, only the speaker changes), else any; the
     *    region of [country] first.
     * 3. [fastStart] (alarms, reminders): if the choice so far would be slow
     *    to start (Kokoro, Supertonic) and a fast voice of the language
     *    exists, the fast one.
     * Voices needing the network or not installed are never chosen.
     */
    fun pickVoice(
        voices: List<VoiceInfo>,
        language: String,
        country: String,
        gender: VoiceGender,
        preferredName: String?,
        engineDefaultName: String?,
        fastStart: Boolean,
    ): String? {
        val installed = voices.filter { !it.requiresNetwork && !it.notInstalled }
        if (preferredName != null) installed.firstOrNull { it.name == preferredName }?.let { return it.name }
        val usable = installed.filter { it.language.equals(language, ignoreCase = true) }
        val default = usable.firstOrNull { it.name == engineDefaultName }
        var choice: VoiceInfo? = null
        if (gender != VoiceGender.ANY) {
            val ofGender = usable.filter { genderOf(it) == gender }
                .sortedWith(compareBy({ !it.country.equals(country, ignoreCase = true) }))
            choice = default?.takeIf { genderOf(it) == gender }
                ?: ofGender.firstOrNull { default != null && modelOf(it.name) == modelOf(default.name) }
                ?: ofGender.firstOrNull()
        }
        if (fastStart) {
            val current = choice ?: default
            if (current == null || !isFast(current)) {
                val fast = usable.filter { isFast(it) }
                    .sortedWith(compareBy({ gender != VoiceGender.ANY && genderOf(it) != gender }, { !it.country.equals(country, ignoreCase = true) }))
                    .firstOrNull()
                if (fast != null) choice = fast
            }
        }
        return choice?.name
    }

    /**
     * The model part of a QVoice voice name: "<model>#<speaker>@<lang>" ->
     * "<model>". Other engines' names come back whole.
     */
    fun modelOf(name: String): String = name.substringBefore('#').substringBefore('@')

    /**
     * A label for a settings list from QVoice's speaker key:
     * "kitten-nano-en#rosie" -> "Rosie"; Kokoro's "#af_heart" -> "Heart"
     * (its two-letter accent/gender prefix dropped); Supertonic's "#f1@hi-IN"
     * -> "F1". Other engines' names come back unchanged.
     */
    fun labelOf(name: String): String {
        var speaker = name.substringAfter('#', "").substringBefore('@')
        if (speaker.isEmpty()) return name
        if (Regex("[a-z]{2}_.+").matches(speaker)) speaker = speaker.substring(3)
        return speaker.replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.titlecase() }
    }
}

/**
 * A voice the user chose, tied to its engine, as one string to store in the
 * field an app already keeps a voice name in: "qvoice:<name>" or
 * "system:<name>". A bare name (stored before RinisoSpeech 1.1, or by an app
 * that never knew engines) counts as a system voice, which is what it was.
 */
data class VoiceRef(val engine: SpeechRouting.Engine, val name: String) {
    override fun toString(): String = (if (engine == SpeechRouting.Engine.QVOICE) QVOICE_PREFIX else SYSTEM_PREFIX) + name

    companion object {
        const val QVOICE_PREFIX = "qvoice:"
        const val SYSTEM_PREFIX = "system:"

        /** Null for null or blank. */
        fun parse(stored: String?): VoiceRef? {
            val s = stored?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return when {
                s.startsWith(QVOICE_PREFIX) -> s.removePrefix(QVOICE_PREFIX).takeIf { it.isNotEmpty() }?.let { VoiceRef(SpeechRouting.Engine.QVOICE, it) }
                s.startsWith(SYSTEM_PREFIX) -> s.removePrefix(SYSTEM_PREFIX).takeIf { it.isNotEmpty() }?.let { VoiceRef(SpeechRouting.Engine.SYSTEM, it) }
                else -> VoiceRef(SpeechRouting.Engine.SYSTEM, s)
            }
        }
    }
}

/**
 * The language a text's script names without doubt, for texts whose language
 * the app doesn't know (cues users type themselves). Null when the script is
 * shared by several languages (Latin, Cyrillic, Arabic, Devanagari: Hindi,
 * Marathi or Nepali) or the text has no letters: then use the app's or the
 * user's language. The most frequent script among the letters decides.
 */
object ScriptLanguage {
    fun guess(text: CharSequence): String? {
        val counts = HashMap<String?, Int>()
        var kana = 0
        for (c in text) {
            if (!Character.isLetter(c)) continue
            val block = Character.UnicodeScript.of(c.code)
            if (block == Character.UnicodeScript.HIRAGANA || block == Character.UnicodeScript.KATAKANA) kana++
            val lang = LANGUAGE[block]
            val key = if (block == Character.UnicodeScript.HAN) "zh" else lang
            counts[key] = (counts[key] ?: 0) + 1
        }
        val top = counts.maxByOrNull { it.value }?.key ?: return null
        // Japanese mixes kana and kanji: any kana makes Han text Japanese.
        if ((top == "zh" || top == "ja") && kana > 0) return "ja"
        return top
    }

    /** Scripts used by one language (or one language far above the others). */
    private val LANGUAGE: Map<Character.UnicodeScript, String> = mapOf(
        Character.UnicodeScript.TAMIL to "ta",
        Character.UnicodeScript.MALAYALAM to "ml",
        Character.UnicodeScript.TELUGU to "te",
        Character.UnicodeScript.KANNADA to "kn",
        Character.UnicodeScript.GUJARATI to "gu",
        Character.UnicodeScript.GURMUKHI to "pa",
        Character.UnicodeScript.ORIYA to "or",
        Character.UnicodeScript.BENGALI to "bn",
        Character.UnicodeScript.SINHALA to "si",
        Character.UnicodeScript.THAI to "th",
        Character.UnicodeScript.LAO to "lo",
        Character.UnicodeScript.KHMER to "km",
        Character.UnicodeScript.MYANMAR to "my",
        Character.UnicodeScript.GEORGIAN to "ka",
        Character.UnicodeScript.ARMENIAN to "hy",
        Character.UnicodeScript.HEBREW to "he",
        Character.UnicodeScript.GREEK to "el",
        Character.UnicodeScript.HANGUL to "ko",
        Character.UnicodeScript.HIRAGANA to "ja",
        Character.UnicodeScript.KATAKANA to "ja",
        Character.UnicodeScript.ETHIOPIC to "am",
    )
}

/** The user's engine setting in a Riniso app (store its name()). */
enum class EngineChoice {
    /** QVoice for every language it has a voice for, the phone's engine for the rest. The default. */
    AUTOMATIC,

    /** Never QVoice: the phone's default engine, as before. */
    SYSTEM,
}

/** A wish, not a promise: honoured when the engine has a voice of that gender for the language. */
enum class VoiceGender { ANY, FEMALE, MALE }

/** Why a request meant for QVoice was spoken by the system engine (RinisoSpeech.Callback.onFallback). */
enum class FallbackReason {
    /** QVoice didn't finish starting within the init timeout. */
    QVOICE_SLOW_TO_START,

    /** QVoice couldn't be started at all. */
    QVOICE_FAILED_TO_START,

    /** Android bound another engine instead of QVoice (it does that silently). */
    QVOICE_REPLACED_BY_ANDROID,

    /** QVoice refused or failed the text (a script it can't read, a voice deleted meanwhile). */
    QVOICE_REFUSED,

    /** No first sound within the request's start timeout (alarms). */
    QVOICE_NO_SOUND_IN_TIME,
}
