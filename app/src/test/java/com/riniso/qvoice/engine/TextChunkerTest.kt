package com.riniso.qvoice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TextChunkerTest {

    @Test
    fun splitsAtSentenceEnds() {
        assertEquals(
            listOf("Hello there.", "How are you?", "Fine!"),
            TextChunker.sentences("Hello there. How are you?  Fine!"),
        )
    }

    @Test
    fun decimalsAbbreviationsInitialsAndNumbersDontSplit() {
        assertEquals(listOf("It costs 3.45 dollars.", "Okay."), TextChunker.sentences("It costs 3.45 dollars. Okay."))
        assertEquals(listOf("Bring fruit, e.g. apples.", "Then go."), TextChunker.sentences("Bring fruit, e.g. apples. Then go."))
        assertEquals(listOf("Dr. Rao met J. K. Singh at 5 p.m. Today."), TextChunker.sentences("Dr. Rao met J. K. Singh at 5 p.m. Today."))
        assertEquals(listOf("\"Why?\" she asked."), TextChunker.sentences("\"Why?\" she asked."))
        assertEquals(listOf("Steps: 1. Open the app.", "Tap Voices."), TextChunker.sentences("Steps: 1. Open the app. Tap Voices."))
    }

    @Test
    fun indicAndCjkTerminators() {
        assertEquals(listOf("नमस्ते।", "आप कैसे हैं?"), TextChunker.sentences("नमस्ते। आप कैसे हैं?"))
        assertEquals(listOf("你好。", "今天天气很好！", "我能帮你吗？"), TextChunker.sentences("你好。今天天气很好！我能帮你吗？"))
    }

    @Test
    fun newlinesAlwaysBreakAndQuotesStayWithTheirSentence() {
        assertEquals(listOf("Chapter one", "It was late."), TextChunker.sentences("Chapter one\r\nIt was late."))
        assertEquals(listOf("He said \"Stop.\"", "Then he left..."), TextChunker.sentences("He said \"Stop.\" Then he left..."))
    }

    @Test
    fun openingSentencesAloneThenPacked() {
        val chunks = TextChunker.split("One. Two is here. Three is here. Four is here. Five.", maxChars = 30)
        assertEquals(listOf("One.", "Two is here.", "Three is here. Four is here.", "Five."), chunks)
    }

    /**
     * The sample text that left ~5 s of silence after "Hello!" on the
     * Galaxy M31: the long second sentence is cut at its comma, so the next
     * sound needs only "This is QVoice," to be generated.
     */
    @Test
    fun longOpeningSentenceIsCutAtItsFirstClause() {
        assertEquals(
            listOf("Hello!", "This is QVoice,", "a natural-sounding voice that works offline."),
            TextChunker.split("Hello! This is QVoice, a natural-sounding voice that works offline."),
        )
        assertEquals(
            listOf("The update is ready —", "restart the app to finish installing it now."),
            TextChunker.split("The update is ready — restart the app to finish installing it now."),
        )
        assertEquals(
            listOf("Here is what you need to know:", "the app restarts after the update finishes."),
            TextChunker.split("Here is what you need to know: the app restarts after the update finishes."),
        )
    }

    @Test
    fun shortSentencesAndShortClausesAreNotCut() {
        // Short: a screen reader's "label, role" stays one piece, and so does
        // a sentence of up to 50 characters even with a usable comma.
        assertEquals(listOf("Settings, button"), TextChunker.split("Settings, button"))
        val short = "Please wait a moment, the voice is loading."
        assertEquals(listOf(short), TextChunker.split(short))
        // "However," is too short to stand alone, and there is no other break.
        val however = "However, the results of the study were clearly positive overall."
        assertEquals(listOf(however), TextChunker.split(however))
        // "1,000" and "10:30" are not breaks; "they said." is too short a tail.
        val numbers = "It rose to 1,000 rupees by 10:30 in the morning on that day, they said."
        assertEquals(listOf(numbers), TextChunker.split(numbers))
        // A hyphen or a range isn't a dash break.
        val hyphen = "The well-known 2019–2020 results were published again on Monday."
        assertEquals(listOf(hyphen), TextChunker.split(hyphen))
    }

    @Test
    fun onlyTheOpeningIsCutAtClauses() {
        val third = "This third sentence is long enough, and it has a comma in it too."
        assertEquals(listOf("One.", "Two.", third), TextChunker.split("One. Two. $third"))
        assertEquals(
            listOf("First, a long enough opening sentence here,", "to be cut once only, at the first break."),
            TextChunker.openingPieces("First, a long enough opening sentence here, to be cut once only, at the first break."),
        )
    }

    @Test
    fun cjkOpeningIsCutAtAFullWidthComma() {
        val first = "我们今天下午三点钟在公园的大门口见面，"
        val rest = "然后一起去看一场新电影，看完电影以后再去附近的餐厅吃晚饭，最后大家一起回家休息吧。"
        val chunks = TextChunker.split(first + rest)
        assertEquals(listOf(first, rest), chunks)
    }

    @Test
    fun longSentenceIsCutAtCommaThenSpace() {
        val sentence = "alpha beta gamma delta, " + "word ".repeat(20) + "end."
        val chunks = TextChunker.split(sentence, maxChars = 40)
        assertEquals("alpha beta gamma delta,", chunks.first())
        assertTrue(chunks.all { it.length <= 40 })
        assertFalse(chunks.any { it.startsWith(" ") || it.endsWith(" ") })
        // "1,000" is not a break.
        val number = "The total came to 1,000 rupees and some more words after it all."
        assertTrue(TextChunker.split(number, maxChars = 30).none { it.endsWith("1,") })
    }

    @Test
    fun blankTextGivesNoChunks() {
        assertEquals(emptyList<String>(), TextChunker.split(""))
        assertEquals(emptyList<String>(), TextChunker.split(" \n\t "))
    }

    @Test
    fun cjkChunksAreJoinedWithoutSpaces() {
        val text = "你好。今天天气很好！我能帮你吗？谢谢。"
        val chunks = TextChunker.split(text, maxChars = 20)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.none { it.contains(' ') })
        // A run without any break is cut at the limit, never inside a surrogate pair.
        val emoji = "😀".repeat(30) // 60 chars, 30 code points
        val cut = TextChunker.split(emoji, maxChars = 21)
        assertTrue(cut.all { !Character.isHighSurrogate(it.last()) && !Character.isLowSurrogate(it.first()) })
        assertEquals(emoji, cut.joinToString(""))
    }

    /**
     * Nothing is lost or reordered: for Latin text, the chunks joined with
     * spaces are the input with its whitespace collapsed. Random texts mix
     * short and over-long sentences, abbreviations and line breaks.
     */
    @Test
    fun noTextIsLostOrReordered() {
        val words = listOf("the", "voice", "Dr.", "e.g.", "3.45", "reads", "every", "word,", "slowly;", "U.S.", "Rao", "ok")
        val ends = listOf(".", "!", "?", "...", "?!")
        val rnd = Random(42)
        repeat(300) {
            val sb = StringBuilder()
            repeat(rnd.nextInt(1, 12)) {
                repeat(rnd.nextInt(1, 70)) { sb.append(words[rnd.nextInt(words.size)]).append(' ') }
                sb.append("End").append(ends[rnd.nextInt(ends.size)])
                sb.append(if (rnd.nextInt(5) == 0) "\n" else "  ")
            }
            val text = sb.toString()
            val max = rnd.nextInt(20, 300)
            val chunks = TextChunker.split(text, maxChars = max)
            assertEquals(text.trim().replace(Regex("\\s+"), " "), chunks.joinToString(" "))
            assertTrue("chunk over $max", chunks.all { it.length <= max })
        }
    }

    /** group() packs whole sentences without split()'s short opening (the reader's long paragraphs). */
    @Test
    fun groupPacksWholeSentencesWithoutTheOpeningCut() {
        val first = "Hello there, my dear old friend, how are you doing on this fine day?" // 68 characters
        val text = "$first I am fine. Thanks for asking me that."
        assertEquals(listOf("$first I am fine.", "Thanks for asking me that."), TextChunker.group(text, 80))
        // split() cuts the opening clause instead, for a fast first sound.
        assertEquals("Hello there,", TextChunker.split(text, 80).first())
    }
}
