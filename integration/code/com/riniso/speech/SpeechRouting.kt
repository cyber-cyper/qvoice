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
     * The engine for a language:
     * - the user chose "Phone's default voice": the system engine, always;
     * - QVoice not installed (or disabled): the system engine;
     * - QVoice says it has no voice for the language ([qvoiceHasLanguage]
     *   false, e.g. Tamil): the system engine, which usually has one;
     * - otherwise QVoice. Unknown support (null, QVoice still starting) is
     *   tried with QVoice: it refuses text it can't read, and RinisoSpeech
     *   then repeats the request on the system engine.
     */
    fun engineFor(choice: EngineChoice, qvoiceInstalled: Boolean, qvoiceHasLanguage: Boolean?): Engine = when {
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

    fun genderOf(v: VoiceInfo): VoiceGender = when {
        GENDER_FEATURE_PREFIX + "female" in v.features -> VoiceGender.FEMALE
        GENDER_FEATURE_PREFIX + "male" in v.features -> VoiceGender.MALE
        else -> VoiceGender.ANY
    }

    /**
     * The voice to set for a request, or null to let the engine use its own
     * default for the language (QVoice's default is the user's choice in
     * QVoice, else the best voice that keeps up on this phone: usually the
     * right answer, so null is the normal case).
     *
     * 1. [preferredName], if the engine still has it (a voice the user picked
     *    in this app's settings).
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
        val usable = voices.filter { it.language.equals(language, ignoreCase = true) && !it.requiresNetwork && !it.notInstalled }
        if (preferredName != null) usable.firstOrNull { it.name == preferredName }?.let { return it.name }
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
            if (current == null || current.latency > LATENCY_NORMAL) {
                val fast = usable.filter { it.latency <= LATENCY_NORMAL }
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

/** The user's engine setting in a Riniso app (store its name()). */
enum class EngineChoice {
    /** QVoice for every language it has a voice for, the phone's engine for the rest. The default. */
    AUTOMATIC,

    /** Never QVoice: the phone's default engine, as before. */
    SYSTEM,
}

/** A wish, not a promise: honoured when the engine has a voice of that gender for the language. */
enum class VoiceGender { ANY, FEMALE, MALE }
