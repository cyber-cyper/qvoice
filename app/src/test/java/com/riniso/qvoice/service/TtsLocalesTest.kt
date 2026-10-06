package com.riniso.qvoice.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsLocalesTest {

    @Test
    fun languagesInAnyFormNormaliseToAlpha2() {
        assertEquals("ta", TtsLocales.toIso2Language("tam"))
        assertEquals("ta", TtsLocales.toIso2Language("TA"))
        assertEquals("en", TtsLocales.toIso2Language("eng"))
        assertEquals("de", TtsLocales.toIso2Language("ger")) // ISO 639-2/B
        assertEquals("de", TtsLocales.toIso2Language("deu"))
        assertEquals("ml", TtsLocales.toIso2Language("mal"))
        assertEquals("hi", TtsLocales.toIso2Language("hi-IN"))
        assertNull(TtsLocales.toIso2Language(""))
        assertNull(TtsLocales.toIso2Language(null))
        assertNull(TtsLocales.toIso2Language("e1"))
    }

    @Test
    fun regionsNormaliseToAlpha2() {
        assertEquals("IN", TtsLocales.toIso2Region("IND"))
        assertEquals("IN", TtsLocales.toIso2Region("in"))
        assertEquals("LK", TtsLocales.toIso2Region("LKA"))
        assertEquals("US", TtsLocales.toIso2Region("USA"))
        assertNull(TtsLocales.toIso2Region(""))
        assertNull(TtsLocales.toIso2Region(null))
    }

    @Test
    fun tagsParseWithDashOrUnderscore() {
        assertEquals("en" to "US", TtsLocales.parseTag("en-US"))
        assertEquals("en" to "US", TtsLocales.parseTag("en_US"))
        assertEquals("ta" to null, TtsLocales.parseTag("ta"))
        assertEquals("zh" to "CN", TtsLocales.parseTag("zh-Hans-CN"))
        assertNull(TtsLocales.parseTag(""))
    }

    @Test
    fun frameworkFormsUseAlpha3() {
        assertArrayEquals(arrayOf("tam", "IND", ""), TtsLocales.frameworkTriple("ta-IN"))
        assertArrayEquals(arrayOf("eng", "USA", ""), TtsLocales.frameworkTriple("en-US"))
        assertArrayEquals(arrayOf("hin", "", ""), TtsLocales.frameworkTriple("hi"))
        assertEquals("eng-USA", TtsLocales.checkDataTag("en-US"))
        assertEquals("mal-IND", TtsLocales.checkDataTag("ml-IN"))
        assertEquals("hin", TtsLocales.checkDataTag("hi"))
    }

    @Test
    fun scriptDetectionSeparatesTamilFromEnglish() {
        assertEquals(ScriptDetector.Script.TAMIL, ScriptDetector.dominantScript("வணக்கம் world"))
        assertEquals(ScriptDetector.Script.LATIN, ScriptDetector.dominantScript("Hello வ"))
        assertNull(ScriptDetector.dominantScript("123 !?"))
        assertTrue(ScriptDetector.canRead("en", "Hello"))
        assertTrue(ScriptDetector.canRead("en", "42"))
        assertFalse(ScriptDetector.canRead("en", "வணக்கம்"))
        assertTrue(ScriptDetector.canRead("ta", "வணக்கம்"))
        assertFalse(ScriptDetector.canRead("ta", "नमस्ते"))
        assertTrue(ScriptDetector.canRead("ja", "日本語"))
        assertTrue(ScriptDetector.canRead("xx-unknown", "anything"))
    }

    @Test
    fun sampleTextsFallBackToEnglish() {
        assertEquals(SampleTexts.forLanguage("en"), SampleTexts.forLanguage("zzz"))
        assertTrue(SampleTexts.forLanguage("tam").contains("வணக்கம்"))
    }
}
