package com.riniso.qvoice.engine

/**
 * Splits text into the pieces QVoice hands to a model one at a time.
 *
 * Every family goes through it since slice 3. Supertonic returns audio only
 * when a whole request is done, so without it a paragraph would sit silent
 * for seconds. The other families stream one sentence at a time themselves
 * (sherpa-onnx maxNumSentences = 1), but a single long sentence is still one
 * long wait before its first sound — and Stop can't take effect until the
 * sentence being generated is finished (sherpa-onnx has no way to interrupt
 * it), so chunk length is also how long Stop can take.
 *
 * Rules, deliberately simple and language-neutral:
 * - a sentence ends at . ! ? … (and the Devanagari danda । ॥, CJK 。！？)
 *   followed by a space, a line break or the end — so "3.45" doesn't split.
 *   CJK marks end a sentence even without a following space;
 * - it does not end where the next word starts with a lower-case letter
 *   ("e.g. apples", "Why? she asked"), nor after an initial ("J. K."), a
 *   dotted abbreviation ("U.S.", "a.m."), a title ("Dr.", "Mr.") or a
 *   number ("1. Open the app");
 * - line breaks always end a sentence;
 * - the opening — the first [OPENING_SENTENCES] sentences — is spoken one
 *   sentence per chunk, and an opening sentence longer than
 *   [OPENING_SPLIT_ABOVE] characters is cut once, at its first clause break
 *   (comma, semicolon, colon, spaced dash) that leaves at least
 *   [MIN_CLAUSE_CHARS] characters on each side. That is what the first
 *   sound waits for, and a short first sentence ("Hello!") followed by a
 *   long one otherwise leaves a gap while the long one is generated (the
 *   sample text did exactly that on the Galaxy M31);
 * - later sentences are packed together up to [maxChars] (fewer model calls);
 * - a single sentence longer than [maxChars] is cut at the last comma or
 *   semicolon before the limit, else the last space, else at the limit
 *   (never inside a surrogate pair).
 *
 * The bias is towards NOT splitting: a missed split only makes one chunk
 * longer, while a wrong split puts a sentence-final fall and a pause in the
 * middle of a sentence ("Dr. | Rao"). Clause breaks are the exception made
 * for the opening only, where the pause is natural and the gain is largest.
 */
object TextChunker {

    /**
     * For models that return a chunk only when all of it is done
     * (Supertonic): long enough for natural phrasing, short enough that the
     * next chunk is ready before this one has been spoken.
     */
    const val DEFAULT_MAX_CHARS = 220

    /**
     * For the families sherpa-onnx streams sentence by sentence: only a
     * single sentence longer than this is cut. ~150 characters is ~10 s of
     * speech, which the built-in voice generates in ~4 s on a Galaxy M31
     * (Piper in ~2 s) — the longest a Stop can wait.
     */
    const val STREAMING_MAX_CHARS = 150

    /** Sentences at the start of a text that are spoken one per chunk (see the class comment). */
    const val OPENING_SENTENCES = 2

    /** An opening sentence longer than this is cut at its first clause break. */
    const val OPENING_SPLIT_ABOVE = 50

    /** Neither side of an opening cut may be shorter than this ("However," stays attached). */
    const val MIN_CLAUSE_CHARS = 12

    private val TERMINATORS = setOf('.', '!', '?', '…', '।', '॥', '。', '！', '？')

    // CJK full stops end a sentence even without a following space.
    private val SPACELESS_TERMINATORS = setOf('。', '！', '？')

    private const val CLOSERS = "\"'”’)]»」』"

    // English titles and short forms that are followed by a capitalised word
    // mid-sentence. Kept short on purpose (see the class comment).
    private val ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "prof", "st", "jr", "sr", "vs", "no", "etc")

    private val DOTTED = Regex("\\p{L}(\\.\\p{L})+")

    private val WHITESPACE = Regex("\\s+")

    fun split(text: String, maxChars: Int = DEFAULT_MAX_CHARS): List<String> {
        require(maxChars >= 20) { "maxChars too small" }
        val sentences = sentences(text)
        val out = ArrayList<String>()
        for (sentence in sentences.take(OPENING_SENTENCES)) {
            for (piece in openingPieces(sentence)) out += hardSplit(piece, maxChars)
        }
        out += pack(sentences.drop(OPENING_SENTENCES), maxChars)
        return out
    }

    /**
     * [text]'s sentences packed into pieces of at most [maxChars]; only a
     * sentence longer than that is cut. Unlike [split], no short opening: for
     * dividing a long paragraph (the reader), not for fast first audio.
     */
    fun group(text: String, maxChars: Int): List<String> {
        require(maxChars >= 20) { "maxChars too small" }
        return pack(sentences(text), maxChars)
    }

    private fun pack(sentences: List<String>, maxChars: Int): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        for (s in sentences.flatMap { hardSplit(it, maxChars) }) {
            val sep = if (current.isEmpty() || joinsWithoutSpace(current.last())) "" else " "
            if (current.isNotEmpty() && current.length + sep.length + s.length > maxChars) {
                out += current.toString()
                current.setLength(0)
                current.append(s)
            } else {
                current.append(sep).append(s)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    /**
     * [sentence] cut once at its first clause break that leaves at least
     * [MIN_CLAUSE_CHARS] on each side — if it is longer than
     * [OPENING_SPLIT_ABOVE] and has such a break; else [sentence] alone.
     */
    internal fun openingPieces(sentence: String): List<String> {
        if (sentence.length <= OPENING_SPLIT_ABOVE) return listOf(sentence)
        for (i in sentence.indices) {
            val cut = clauseBreakAfter(sentence, i)
            if (cut < 0) continue
            val head = sentence.substring(0, cut).trim()
            val tail = sentence.substring(cut).trim()
            if (head.length < MIN_CLAUSE_CHARS) continue // "However," stays attached
            if (tail.length < MIN_CLAUSE_CHARS) break // later breaks leave even less
            return listOf(head, tail)
        }
        return listOf(sentence)
    }

    /** The index just after [i] if a clause ends with the character at [i], else -1. */
    private fun clauseBreakAfter(s: String, i: Int): Int {
        val c = s[i]
        val next = s.getOrNull(i + 1)
        return when {
            // CJK marks are followed by the next word directly.
            c in "，、；：" -> i + 1
            // Latin-style marks need a following space: "1,000" and "10:30" are not breaks.
            c in ",;:،" && next == ' ' -> i + 1
            // A dash with a space on both sides (" — ", " – "), not a hyphen or a range.
            (c == '—' || c == '–') && next == ' ' && s.getOrNull(i - 1) == ' ' -> i + 1
            else -> -1
        }
    }

    internal fun sentences(text: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            val s = current.toString().replace(WHITESPACE, " ").trim()
            if (s.isNotEmpty()) out += s
            current.setLength(0)
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n' || c == '\r') {
                flush()
                i++
                continue
            }
            current.append(c)
            if (c in TERMINATORS) {
                val markAt = current.length - 1
                // Absorb closing quotes/brackets and repeated marks ("?!", "...").
                while (i + 1 < text.length && (text[i + 1] in TERMINATORS || text[i + 1] in CLOSERS)) {
                    i++
                    current.append(text[i])
                }
                val next = text.getOrNull(i + 1)
                val ends = when {
                    c in SPACELESS_TERMINATORS -> true
                    next != null && !next.isWhitespace() -> false
                    nextWordIsLowerCase(text, i + 1) -> false
                    c == '.' && markAt == current.length - 1 && isAbbreviation(current, markAt) -> false
                    else -> true
                }
                if (ends) flush()
            }
            i++
        }
        flush()
        return out
    }

    /** True if, after spaces on the same line, the next character is a lower-case letter. */
    private fun nextWordIsLowerCase(text: String, from: Int): Boolean {
        var j = from
        while (j < text.length && text[j].isWhitespace() && text[j] != '\n' && text[j] != '\r') j++
        return j < text.length && text[j].isLowerCase()
    }

    /** The word ending at [dotAt] (exclusive) is an initial, a dotted abbreviation or a title. */
    private fun isAbbreviation(sb: StringBuilder, dotAt: Int): Boolean {
        var start = dotAt
        while (start > 0 && !sb[start - 1].isWhitespace()) start--
        val word = sb.substring(start, dotAt).trimStart('(', '"', '\'', '“', '‘', '[')
        if (word.isEmpty()) return false
        if (word.length == 1 && word[0].isLetter()) return word[0].isUpperCase()
        // "1. Open the app" (a list marker) or "born in 1990. Then" — merging is harmless.
        if (word.all { it.isDigit() }) return true
        return DOTTED.matches(word) || word.lowercase() in ABBREVIATIONS
    }

    /** CJK text runs on without spaces; so must the chunks QVoice builds from it. */
    private fun joinsWithoutSpace(c: Char): Boolean {
        if (c in SPACELESS_TERMINATORS || c in "，、；：") return true
        return when (Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA -> true
            else -> false
        }
    }

    private fun hardSplit(sentence: String, maxChars: Int): List<String> {
        if (sentence.length <= maxChars) return listOf(sentence)
        val out = ArrayList<String>()
        var rest = sentence
        while (rest.length > maxChars) {
            var cut = softBreak(rest, maxChars)
                ?: rest.lastIndexOf(' ', maxChars).takeIf { it > maxChars / 3 }
                ?: maxChars
            if (Character.isHighSurrogate(rest[cut - 1])) cut-- // don't split an emoji or a rare CJK character
            val piece = rest.substring(0, cut).trim()
            if (piece.isNotEmpty()) out += piece
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }

    /** Index just after the last comma/semicolon in the first [maxChars] chars, if it's past a third. */
    private fun softBreak(s: String, maxChars: Int): Int? {
        var i = minOf(maxChars, s.length) - 1
        while (i > maxChars / 3) {
            val c = s[i]
            // Latin-style marks need a following space ("1,000" is not a break).
            if (c in "，、；") return i + 1
            if (c in ",;،" && i + 1 < s.length && s[i + 1] == ' ') return i + 1
            i--
        }
        return null
    }
}
