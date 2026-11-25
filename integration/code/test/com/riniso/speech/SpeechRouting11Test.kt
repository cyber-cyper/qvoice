// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Riniso. Tests of what RinisoSpeech 1.1.0 added (JUnit 4).
package com.riniso.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRouting11Test {

    private val qv = SpeechRouting.Engine.QVOICE
    private val sys = SpeechRouting.Engine.SYSTEM

    private fun v(name: String, language: String = "en", gender: String? = null) = SpeechRouting.VoiceInfo(
        name, language, "US", gender?.let { setOf(SpeechRouting.GENDER_FEATURE_PREFIX + it) }.orEmpty(), 300, false, false,
    )

    @Test
    fun aChosenVoiceKeepsItsEngine() {
        // A Google voice chosen by the user stays Google, even in Automatic with QVoice installed.
        assertEquals(sys, SpeechRouting.engineFor(EngineChoice.AUTOMATIC, true, true, pinned = sys))
        // A QVoice voice is QVoice, even for a language QVoice wasn't asked about...
        assertEquals(qv, SpeechRouting.engineFor(EngineChoice.SYSTEM, true, false, pinned = qv))
        // ...unless QVoice is gone: then the phone's voice rather than silence.
        assertEquals(sys, SpeechRouting.engineFor(EngineChoice.AUTOMATIC, false, null, pinned = qv))
    }

    @Test
    fun voiceRefsRoundTripAndOldNamesAreSystemVoices() {
        val q = VoiceRef(qv, "kitten-nano-en#rosie")
        assertEquals("qvoice:kitten-nano-en#rosie", q.toString())
        assertEquals(q, VoiceRef.parse(q.toString()))
        assertEquals(VoiceRef(sys, "en-us-x-iob-local"), VoiceRef.parse("system:en-us-x-iob-local"))
        assertEquals("a bare name stored before 1.1", VoiceRef(sys, "en-us-x-iob-local"), VoiceRef.parse("en-us-x-iob-local"))
        assertNull(VoiceRef.parse(null))
        assertNull(VoiceRef.parse("  "))
        assertNull(VoiceRef.parse("qvoice:"))
    }

    @Test
    fun aChosenVoiceIsUsedInAnyLanguage() {
        val voices = listOf(v("kokoro#ef_dora", language = "es", gender = "female"), v("kitten-nano-en#rosie"))
        assertEquals("kokoro#ef_dora", SpeechRouting.pickVoice(voices, "en", "US", VoiceGender.ANY, "kokoro#ef_dora", null, false))
    }

    @Test
    fun qvoiceIsRecognisedByItsBuiltInVoice() {
        assertTrue(SpeechRouting.isQVoice(listOf(v("kokoro-multi-lang-v1_0#af_heart"), v("kitten-nano-en#bruno"))))
        assertFalse("Google bound in its place", SpeechRouting.isQVoice(listOf(v("en-us-x-iob-local"))))
        assertFalse(SpeechRouting.isQVoice(emptyList()))
    }

    @Test
    fun scriptsNameTheirLanguageOnlyWhenThereIsNoDoubt() {
        assertEquals("ta", ScriptLanguage.guess("காலை வணக்கம்!"))
        assertEquals("the most frequent script decides", "ta", ScriptLanguage.guess("எழுந்திரு, நேரமாகிவிட்டது (wake up)"))
        assertNull(ScriptLanguage.guess("Wake up now, said Ravi: காலை"))
        assertEquals("ml", ScriptLanguage.guess("സുപ്രഭാതം"))
        assertEquals("te", ScriptLanguage.guess("శుభోదయం"))
        assertEquals("ja", ScriptLanguage.guess("おはよう世界"))
        assertEquals("zh", ScriptLanguage.guess("早上好"))
        assertEquals("ko", ScriptLanguage.guess("좋은 아침"))
        assertEquals("th", ScriptLanguage.guess("สวัสดีตอนเช้า"))
        assertNull("Latin: English, Spanish, ...", ScriptLanguage.guess("Good morning"))
        assertNull("Devanagari: Hindi, Marathi or Nepali", ScriptLanguage.guess("सुप्रभात"))
        assertNull("Arabic script: Arabic, Urdu, Persian", ScriptLanguage.guess("صبح بخیر"))
        assertNull(ScriptLanguage.guess("12:30 !!"))
    }
}
