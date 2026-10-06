package com.riniso.qvoice.voices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VoiceManifestJsonTest {

    private val kitten = BundledVoices.KITTEN_NANO_EN

    @Test
    fun roundTripKeepsEveryField() {
        val parsed = VoiceManifestJson.parse(VoiceManifestJson.toJson(kitten))
        assertEquals(kitten, parsed)
    }

    @Test
    fun defaultSpeakerFallsBackToFirstWhenKeyUnknown() {
        val m = kitten.copy(defaultSpeakerKey = "nobody")
        assertEquals("rosie", m.defaultSpeaker?.key)
    }

    @Test
    fun rejectsManifestFromNewerSchema() {
        val json = VoiceManifestJson.toJson(kitten).replace("\"schema\": 1", "\"schema\": 99")
        expectInvalid(json, "newer")
    }

    @Test
    fun rejectsUnknownFamily() {
        val json = VoiceManifestJson.toJson(kitten).replace("\"family\": \"kitten\"", "\"family\": \"mystery\"")
        expectInvalid(json, "unknown family")
    }

    @Test
    fun rejectsPathTraversalInFileNames() {
        val json = VoiceManifestJson.toJson(kitten).replace("\"${kitten.files.model}\"", "\"../../shared_prefs/x.xml\"")
        expectInvalid(json, "unsafe file name")
    }

    @Test
    fun rejectsAbsoluteFileNames() {
        val json = VoiceManifestJson.toJson(kitten).replace("\"tokens.txt\"", "\"/data/evil\"")
        expectInvalid(json, "unsafe file name")
    }

    @Test
    fun rejectsDuplicateSpeakerKeys() {
        val m = kitten.copy(speakers = kitten.speakers + kitten.speakers.first())
        expectInvalid(VoiceManifestJson.toJson(m), "duplicate speaker key")
    }

    @Test
    fun rejectsUnsafeVoiceId() {
        val json = VoiceManifestJson.toJson(kitten).replace("\"id\": \"${kitten.id}\"", "\"id\": \"a#b\"")
        expectInvalid(json, "unsafe voice id")
    }

    @Test
    fun explicitJsonNullIsTreatedAsAbsent() {
        // Android's org.json returns the string "null" from optString for a
        // JSON null; the parser must not turn that into a speaker key.
        val json = VoiceManifestJson.toJson(kitten).replace("\"defaultSpeaker\": \"rosie\"", "\"defaultSpeaker\": null")
        val parsed = VoiceManifestJson.parse(json)
        assertNull(parsed.defaultSpeakerKey)
        assertEquals("rosie", parsed.defaultSpeaker?.key)
    }

    @Test
    fun cosmeticFieldsAreOptional() {
        val minimal = """
            {"schema":1,"id":"v1","family":"vits","sampleRate":22050,
             "languages":["hi-IN"],"files":{"model":"m.onnx","tokens":"tokens.txt"}}
        """.trimIndent()
        val m = VoiceManifestJson.parse(minimal)
        assertEquals("v1", m.displayName)
        assertEquals(VoiceQuality.NORMAL, m.quality)
        assertEquals(EspeakData.BUNDLED, m.espeakData)
        assertTrue(m.speakers.isEmpty())
        assertEquals(listOf("hi-IN"), m.languagesOf(null))
    }

    @Test
    fun slice1SpeakerLanguageSpellingStillParses() {
        val json = """
            {"schema":1,"id":"k","family":"kokoro","sampleRate":24000,"languages":["en-US","hi-IN"],
             "files":{"model":"m.onnx","tokens":"t.txt","voices":"v.bin"},
             "speakers":[{"sid":31,"key":"hf_alpha","name":"Alpha","gender":"female","language":"hi-IN"}]}
        """.trimIndent()
        assertEquals(listOf("hi-IN"), VoiceManifestJson.parse(json).speakers.single().languages)
    }

    @Test
    fun languageConfigAndSpeakerQualityRoundTrip() {
        val m = kitten.copy(
            family = VoiceFamily.KOKORO,
            languages = listOf("en-US", "en-GB", "hi-IN"),
            speakers = listOf(
                Speaker(3, "af_heart", "Heart", Gender.FEMALE, languages = listOf("en-US")),
                Speaker(31, "hf_alpha", "Alpha", Gender.FEMALE, languages = listOf("hi-IN"), quality = VoiceQuality.NORMAL),
            ),
            defaultSpeakerKey = "af_heart",
            languageConfig = mapOf(
                "en-GB" to LanguageConfig(espeakVoice = "en", lexicons = listOf("lexicon-gb-en.txt")),
                "hi" to LanguageConfig(espeakVoice = "hi"),
            ),
        )
        val parsed = VoiceManifestJson.parse(VoiceManifestJson.toJson(m))
        assertEquals(m, parsed)
        assertEquals("en", parsed.configFor("en-GB")?.espeakVoice)
        assertEquals("hi", parsed.configFor("hi-IN")?.espeakVoice) // falls back to the bare language
        assertNull(parsed.configFor("fr-FR"))
    }

    @Test
    fun safeIdAlphabet() {
        assertTrue(VoiceManifestJson.isSafeId("vits-piper-en_US-amy-medium"))
        assertTrue(!VoiceManifestJson.isSafeId(".hidden"))
        assertTrue(!VoiceManifestJson.isSafeId("a/b"))
        assertTrue(!VoiceManifestJson.isSafeId("a#b"))
        assertTrue(!VoiceManifestJson.isSafeId("வணக்கம்"))
        assertTrue(!VoiceManifestJson.isSafeId(""))
    }

    private fun expectInvalid(json: String, messagePart: String) {
        try {
            VoiceManifestJson.parse(json)
            fail("expected InvalidManifestException containing '$messagePart'")
        } catch (e: VoiceManifestJson.InvalidManifestException) {
            assertTrue("message was: ${e.message}", e.message.orEmpty().contains(messagePart))
        }
    }
}
