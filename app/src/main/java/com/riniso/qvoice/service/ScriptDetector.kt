package com.riniso.qvoice.service

/**
 * Works out which writing system a text is in, so QVoice never reads Tamil
 * text aloud with an English voice (which produces confident gibberish).
 *
 * The situation it guards: an app on a Tamil-locale phone calls speak()
 * without setting a language, so the request says "tam". With no Tamil voice
 * installed, falling back to the English voice is right for "Hello" but wrong
 * for "வணக்கம்". Counting letters by Unicode script tells the two apart.
 */
object ScriptDetector {

    enum class Script {
        LATIN, CYRILLIC, GREEK, ARMENIAN, GEORGIAN, HEBREW, ARABIC,
        DEVANAGARI, BENGALI, GURMUKHI, GUJARATI, ORIYA, TAMIL, TELUGU, KANNADA, MALAYALAM, SINHALA,
        THAI, LAO, KHMER, MYANMAR, TIBETAN,
        HAN, JAPANESE_KANA, HANGUL,
        OTHER,
    }

    /**
     * The script most of [text] is written in, or null if it contains no
     * script-specific characters at all (only digits, punctuation, spaces).
     *
     * Counts every code point that belongs to a script, not just letters:
     * in Indic scripts vowel signs and viramas are combining marks, so a
     * letters-only count makes "வணக்கம்" (7 code points) look as short as
     * "world" and ties go the wrong way.
     */
    fun dominantScript(text: CharSequence): Script? {
        val counts = IntArray(Script.entries.size)
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            i += Character.charCount(cp)
            val script = Character.UnicodeScript.of(cp)
            if (script == Character.UnicodeScript.COMMON ||
                script == Character.UnicodeScript.INHERITED ||
                script == Character.UnicodeScript.UNKNOWN
            ) {
                continue
            }
            counts[scriptOf(script).ordinal]++
        }
        var best = -1
        var bestCount = 0
        for (k in counts.indices) {
            if (counts[k] > bestCount) {
                best = k
                bestCount = counts[k]
            }
        }
        return if (best < 0) null else Script.entries[best]
    }

    /** The script a language is normally written in; null when unknown (callers then don't block). */
    fun scriptOfLanguage(language: String): Script? = when (language.lowercase()) {
        "hi", "mr", "ne", "sa", "kok", "mai", "bho" -> Script.DEVANAGARI
        "bn", "as" -> Script.BENGALI
        "pa" -> Script.GURMUKHI
        "gu" -> Script.GUJARATI
        "or" -> Script.ORIYA
        "ta" -> Script.TAMIL
        "te" -> Script.TELUGU
        "kn" -> Script.KANNADA
        "ml" -> Script.MALAYALAM
        "si" -> Script.SINHALA
        "ru", "uk", "bg", "be", "mk", "kk", "ky", "mn", "sr" -> Script.CYRILLIC
        "el" -> Script.GREEK
        "hy" -> Script.ARMENIAN
        "ka" -> Script.GEORGIAN
        "he", "yi" -> Script.HEBREW
        "ar", "fa", "ur", "ps", "sd" -> Script.ARABIC
        "th" -> Script.THAI
        "lo" -> Script.LAO
        "km" -> Script.KHMER
        "my" -> Script.MYANMAR
        "bo" -> Script.TIBETAN
        "zh", "yue" -> Script.HAN
        "ja" -> Script.JAPANESE_KANA
        "ko" -> Script.HANGUL
        "en", "es", "fr", "de", "it", "pt", "nl", "sv", "da", "no", "nb", "nn", "fi", "et", "lv", "lt",
        "pl", "cs", "sk", "sl", "hr", "bs", "hu", "ro", "sq", "tr", "az", "uz", "vi", "id", "ms",
        "tl", "sw", "cy", "ga", "is", "lb", "ca", "eu", "gl", "mt", "af", "zu", "yo", "eo" -> Script.LATIN
        else -> null
    }

    /**
     * True when a voice speaking [language] can sensibly read [text]: the
     * text has no letters (numbers, punctuation), or its letters are mostly
     * in the language's script. Japanese accepts Han characters too.
     */
    fun canRead(language: String, text: CharSequence): Boolean {
        val textScript = dominantScript(text) ?: return true
        val voiceScript = scriptOfLanguage(language) ?: return true
        if (textScript == voiceScript) return true
        return voiceScript == Script.JAPANESE_KANA && textScript == Script.HAN
    }

    private fun scriptOf(script: Character.UnicodeScript): Script = when (script) {
        Character.UnicodeScript.LATIN -> Script.LATIN
        Character.UnicodeScript.CYRILLIC -> Script.CYRILLIC
        Character.UnicodeScript.GREEK -> Script.GREEK
        Character.UnicodeScript.ARMENIAN -> Script.ARMENIAN
        Character.UnicodeScript.GEORGIAN -> Script.GEORGIAN
        Character.UnicodeScript.HEBREW -> Script.HEBREW
        Character.UnicodeScript.ARABIC -> Script.ARABIC
        Character.UnicodeScript.DEVANAGARI -> Script.DEVANAGARI
        Character.UnicodeScript.BENGALI -> Script.BENGALI
        Character.UnicodeScript.GURMUKHI -> Script.GURMUKHI
        Character.UnicodeScript.GUJARATI -> Script.GUJARATI
        Character.UnicodeScript.ORIYA -> Script.ORIYA
        Character.UnicodeScript.TAMIL -> Script.TAMIL
        Character.UnicodeScript.TELUGU -> Script.TELUGU
        Character.UnicodeScript.KANNADA -> Script.KANNADA
        Character.UnicodeScript.MALAYALAM -> Script.MALAYALAM
        Character.UnicodeScript.SINHALA -> Script.SINHALA
        Character.UnicodeScript.THAI -> Script.THAI
        Character.UnicodeScript.LAO -> Script.LAO
        Character.UnicodeScript.KHMER -> Script.KHMER
        Character.UnicodeScript.MYANMAR -> Script.MYANMAR
        Character.UnicodeScript.TIBETAN -> Script.TIBETAN
        Character.UnicodeScript.HAN -> Script.HAN
        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> Script.JAPANESE_KANA
        Character.UnicodeScript.HANGUL -> Script.HANGUL
        else -> Script.OTHER
    }
}
