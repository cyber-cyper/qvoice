package com.riniso.qvoice.reader

import com.riniso.qvoice.engine.TextChunker
import com.riniso.qvoice.service.ScriptDetector
import java.util.Locale

/**
 * Turns shared or selected text into the paragraphs the reader speaks, one
 * utterance each, highlighting the one being read.
 */
object ReaderText {

    /**
     * Longer texts are cut to this (about 1¾ hours of speech at normal
     * speed): far past what anyone shares to listen to, and a whole book
     * pasted in would otherwise stall the screen.
     */
    const val MAX_CHARS = 100_000

    /**
     * Paragraphs longer than this are divided at sentence ends, so the
     * highlight stays useful and no utterance nears TextToSpeech's limit of
     * 4,000 characters.
     */
    const val MAX_PARAGRAPH = 1_000

    /** With a screen reader on, pieces this long at most (~20 s of speech): TalkBack waits while one plays. */
    const val SCREEN_READER_PARAGRAPH = 300

    data class Prepared(val paragraphs: List<String>, val truncated: Boolean)

    // A blank line (possibly holding spaces) separates paragraphs; a single
    // line break doesn't: shared text is often hard-wrapped mid-sentence.
    private val BLANK_LINE = Regex("\\n[ \\t\\u00A0]*\\n\\s*")
    private val WHITESPACE = Regex("\\s+")

    fun prepare(raw: CharSequence, maxParagraph: Int = MAX_PARAGRAPH): Prepared {
        var end = minOf(raw.length, MAX_CHARS)
        if (end < raw.length && end > 0 && Character.isHighSurrogate(raw[end - 1])) end-- // never half a character
        val text = raw.subSequence(0, end).toString()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .filter { it == '\n' || it == '\t' || !Character.isISOControl(it) }
        val paragraphs = text.split(BLANK_LINE)
            .map { it.replace(WHITESPACE, " ").trim() }
            // "***" or "- - -" between sections: nothing to say.
            .filter { paragraph -> paragraph.any { it.isLetterOrDigit() } }
            .flatMap { if (it.length <= maxParagraph) listOf(it) else TextChunker.group(it, maxParagraph) }
        return Prepared(paragraphs, truncated = end < raw.length)
    }

    /**
     * The start of [paragraph] for a one-line preview (Home's Read aloud
     * card): whole words, at most [maxChars] characters, "…" when cut.
     */
    fun snippet(paragraph: String, maxChars: Int = SNIPPET_CHARS): String {
        val text = paragraph.trim()
        if (text.length <= maxChars) return text
        var end = text.lastIndexOf(' ', maxChars).takeIf { it > maxChars / 2 } ?: maxChars
        if (Character.isHighSurrogate(text[end - 1])) end-- // never half a character
        return text.substring(0, end).trimEnd() + "…"
    }

    const val SNIPPET_CHARS = 80
}

/**
 * Which language to ask the engine for, paragraph by paragraph. A request
 * carries one language, and text shared from anywhere can be in any script: a
 * Hindi paragraph asked for in English would be skipped or read as noise.
 */
object ReaderLanguage {

    /**
     * @param phone the phone's locale; used whenever its language has a voice
     *   and can read [paragraph] (the region then picks the accent).
     * @param voiceLanguages the languages (ISO 639-1) installed voices speak.
     * @return the phone's locale; else an installed language written in the
     *   paragraph's script (English first for Latin text: every install has
     *   it); null if no voice can read the paragraph.
     */
    fun pick(paragraph: String, phone: Locale, voiceLanguages: Collection<String>): Locale? {
        val phoneLanguage = phone.language
        if (phoneLanguage in voiceLanguages && ScriptDetector.canRead(phoneLanguage, paragraph)) return phone
        val script = ScriptDetector.dominantScript(paragraph)
        val candidates = (listOf("en") + voiceLanguages.sorted()).distinct().filter { it in voiceLanguages }
        // Written in exactly that script first: Han-only text is Chinese far
        // more often than Japanese, which (rightly) "can read" Han too.
        val language = candidates.firstOrNull { script != null && ScriptDetector.scriptOfLanguage(it) == script }
            ?: candidates.firstOrNull { language ->
                // A language of unknown script "can read" anything; trust that only for script-less text.
                (script == null || ScriptDetector.scriptOfLanguage(language) != null) &&
                    ScriptDetector.canRead(language, paragraph)
            }
            ?: return null
        return Locale.forLanguageTag(language)
    }
}
