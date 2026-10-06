package com.riniso.qvoice.service

import java.util.Locale

/**
 * Translates between the language codes QVoice's manifests use (BCP-47,
 * "ta-IN") and the ones Android's TTS framework speaks.
 *
 * The framework is inconsistent by design: TextToSpeech.setLanguage and
 * SynthesisRequest hand the engine ISO-639-2 + ISO-3166 alpha-3 codes
 * ("tam", "IND"), some callers pass alpha-2 ("ta", "IN"), and
 * CHECK_TTS_DATA expects "tam-IND". Everything funnels through here so the
 * service and the check activity can never disagree (Marmalade shipped
 * exactly that disagreement once).
 *
 * Explicit tables instead of Locale.getISO3Language(), because the JDK/ICU
 * tables differ between Android releases and OEMs; Locale is only a fallback
 * for codes the tables don't know.
 */
object TtsLocales {

    /** Normalises "ta", "tam", "TA", "ta-IN" (first part) to "ta". Null if unrecognisable. */
    fun toIso2Language(code: String?): String? {
        val c = code?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_')
            ?.takeIf { it.isNotEmpty() } ?: return null
        if (c.length == 2) return if (c.all { it in 'a'..'z' }) c else null
        LANG_3_TO_2[c]?.let { return it }
        if (c.length == 3 && c.all { it in 'a'..'z' }) {
            // Unknown alpha-3: ask Locale, which knows the full ISO-639 list.
            // Cached: the framework asks the same question on every request.
            return unknownAlpha3Cache.getOrPut(c) {
                val fromLocale = Locale.getAvailableLocales().firstOrNull {
                    runCatching { it.isO3Language }.getOrNull() == c
                }?.language
                // Languages with no 2-letter code stay alpha-3 (BCP-47 allows it).
                if (!fromLocale.isNullOrEmpty() && fromLocale.length == 2) fromLocale else c
            }
        }
        return null
    }

    private val unknownAlpha3Cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Normalises "IN", "IND", "in" to "IN". Null when absent or unrecognisable. */
    fun toIso2Region(code: String?): String? {
        val c = code?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        if (c.length == 2 && c.all { it in 'A'..'Z' }) return c
        REGION_3_TO_2[c]?.let { return it }
        if (c.length == 3 && c.all { it in 'A'..'Z' }) {
            val fromLocale = Locale.getISOCountries().firstOrNull {
                runCatching { legacyLocale("", it).isO3Country }.getOrNull() == c
            }
            if (fromLocale != null) return fromLocale
        }
        return null
    }

    /** Splits "en-US" / "en_US" / "en" into ("en", "US"?). Null when the language part is unusable. */
    fun parseTag(tag: String?): Pair<String, String?>? {
        if (tag.isNullOrBlank()) return null
        val parts = tag.trim().split('-', '_')
        val language = toIso2Language(parts[0]) ?: return null
        val region = parts.drop(1).firstOrNull { it.length == 2 || (it.length == 3 && it.all(Char::isLetter)) }
            ?.let { toIso2Region(it) }
        return language to region
    }

    /** "ta", "IN" -> "ta-IN"; "ta", null -> "ta". */
    fun tagOf(language: String, region: String?): String =
        if (region.isNullOrEmpty()) language else "$language-$region"

    /**
     * `Locale(language, country)` in one place. The constructors are
     * deprecated since Java 19 (compileSdk 36 warns), but their replacement
     * `Locale.of` only exists from API 36, and `Locale.Builder` rejects some
     * legacy codes the framework still sends, so the constructor stays.
     */
    @Suppress("DEPRECATION")
    private fun legacyLocale(language: String, country: String = ""): Locale = Locale(language, country)

    fun localeFor(tag: String): Locale {
        val (language, region) = parseTag(tag) ?: return Locale.US
        return legacyLocale(language, region.orEmpty())
    }

    fun iso3Language(language2: String): String =
        LANG_2_TO_3[language2]
            ?: runCatching { legacyLocale(language2).isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: language2

    fun iso3Region(region2: String): String =
        REGION_2_TO_3[region2]
            ?: runCatching { legacyLocale("", region2).isO3Country }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: region2

    /**
     * The [language, country, variant] triple TextToSpeechService.onGetLanguage
     * returns, and the "tam-IND" form CHECK_TTS_DATA wants.
     */
    fun frameworkTriple(tag: String): Array<String> {
        val (language, region) = parseTag(tag) ?: return arrayOf("eng", "USA", "")
        return arrayOf(iso3Language(language), region?.let { iso3Region(it) } ?: "", "")
    }

    fun checkDataTag(tag: String): String? {
        parseTag(tag) ?: return null
        val (l3, r3, _) = frameworkTriple(tag)
        return if (r3.isEmpty()) l3 else "$l3-$r3"
    }

    // ISO 639-1 -> ISO 639-2/T, the form Locale.getISO3Language() returns.
    // Covers every language in the sherpa-onnx voice catalogue plus the
    // Indian languages QVoice's sister apps care about.
    private val LANG_2_TO_3: Map<String, String> = mapOf(
        "af" to "afr", "am" to "amh", "ar" to "ara", "as" to "asm", "az" to "aze",
        "be" to "bel", "bg" to "bul", "bn" to "ben", "bo" to "bod", "bs" to "bos",
        "ca" to "cat", "cs" to "ces", "cy" to "cym", "da" to "dan", "de" to "deu",
        "el" to "ell", "en" to "eng", "eo" to "epo", "es" to "spa", "et" to "est",
        "eu" to "eus", "fa" to "fas", "fi" to "fin", "fr" to "fra", "ga" to "gle",
        "gl" to "glg", "gu" to "guj", "he" to "heb", "hi" to "hin", "hr" to "hrv",
        "hu" to "hun", "hy" to "hye", "id" to "ind", "is" to "isl", "it" to "ita",
        "ja" to "jpn", "ka" to "kat", "kk" to "kaz", "km" to "khm", "kn" to "kan",
        "ko" to "kor", "ky" to "kir", "lb" to "ltz", "lo" to "lao", "lt" to "lit",
        "lv" to "lav", "mk" to "mkd", "ml" to "mal", "mn" to "mon", "mr" to "mar",
        "ms" to "msa", "mt" to "mlt", "my" to "mya", "nb" to "nob", "ne" to "nep",
        "nl" to "nld", "nn" to "nno", "no" to "nor", "or" to "ori", "pa" to "pan",
        "pl" to "pol", "ps" to "pus", "pt" to "por", "ro" to "ron", "ru" to "rus",
        "sa" to "san", "sd" to "snd", "si" to "sin", "sk" to "slk", "sl" to "slv",
        "sq" to "sqi", "sr" to "srp", "sv" to "swe", "sw" to "swa", "ta" to "tam",
        "te" to "tel", "th" to "tha", "tl" to "tgl", "tr" to "tur", "uk" to "ukr",
        "ur" to "urd", "uz" to "uzb", "vi" to "vie", "yo" to "yor", "zh" to "zho",
        "zu" to "zul",
    )

    // Alpha-3 inputs, including the ISO 639-2/B spellings some apps still send.
    private val LANG_3_TO_2: Map<String, String> =
        LANG_2_TO_3.entries.associate { (k, v) -> v to k } + mapOf(
            "ger" to "de", "fre" to "fr", "chi" to "zh", "cze" to "cs", "dut" to "nl",
            "per" to "fa", "may" to "ms", "rum" to "ro", "alb" to "sq", "arm" to "hy",
            "geo" to "ka", "mac" to "mk", "bur" to "my", "baq" to "eu", "ice" to "is",
            "wel" to "cy", "slo" to "sk", "tib" to "bo", "gre" to "el", "fil" to "tl",
        )

    private val REGION_2_TO_3: Map<String, String> = mapOf(
        "AE" to "ARE", "AR" to "ARG", "AT" to "AUT", "AU" to "AUS", "BD" to "BGD",
        "BE" to "BEL", "BG" to "BGR", "BR" to "BRA", "CA" to "CAN", "CH" to "CHE",
        "CL" to "CHL", "CN" to "CHN", "CO" to "COL", "CZ" to "CZE", "DE" to "DEU",
        "DK" to "DNK", "EE" to "EST", "EG" to "EGY", "ES" to "ESP", "FI" to "FIN",
        "FR" to "FRA", "GB" to "GBR", "GE" to "GEO", "GR" to "GRC", "HK" to "HKG",
        "HR" to "HRV", "HU" to "HUN", "ID" to "IDN", "IE" to "IRL", "IL" to "ISR",
        "IN" to "IND", "IR" to "IRN", "IS" to "ISL", "IT" to "ITA", "JO" to "JOR",
        "JP" to "JPN", "KE" to "KEN", "KR" to "KOR", "KZ" to "KAZ", "LK" to "LKA",
        "LT" to "LTU", "LU" to "LUX", "LV" to "LVA", "MA" to "MAR", "MX" to "MEX",
        "MY" to "MYS", "NG" to "NGA", "NL" to "NLD", "NO" to "NOR", "NP" to "NPL",
        "NZ" to "NZL", "PE" to "PER", "PH" to "PHL", "PK" to "PAK", "PL" to "POL",
        "PT" to "PRT", "RO" to "ROU", "RS" to "SRB", "RU" to "RUS", "SA" to "SAU",
        "SE" to "SWE", "SG" to "SGP", "SI" to "SVN", "SK" to "SVK", "TH" to "THA",
        "TR" to "TUR", "TW" to "TWN", "UA" to "UKR", "US" to "USA", "VN" to "VNM",
        "ZA" to "ZAF",
    )

    private val REGION_3_TO_2: Map<String, String> =
        REGION_2_TO_3.entries.associate { (k, v) -> v to k }
}
