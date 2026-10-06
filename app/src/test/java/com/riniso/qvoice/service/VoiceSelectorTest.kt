package com.riniso.qvoice.service

import com.riniso.qvoice.voices.BundledVoices
import com.riniso.qvoice.voices.Gender
import com.riniso.qvoice.voices.InstalledVoice
import com.riniso.qvoice.voices.Speaker
import com.riniso.qvoice.voices.VoiceFamily
import com.riniso.qvoice.voices.VoiceFiles
import com.riniso.qvoice.voices.VoiceManifest
import com.riniso.qvoice.voices.VoiceQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceSelectorTest {

    private class MapDefaults(val map: MutableMap<String, String> = HashMap()) : VoiceDefaults {
        override fun defaultVoiceFor(languageKey: String): String? = map[languageKey]
    }

    private val kitten = InstalledVoice(BundledVoices.KITTEN_NANO_EN, File("k"), ready = true, assetDir = BundledVoices.DEFAULT.single().assetDir)

    private fun voice(
        id: String,
        languages: List<String>,
        quality: VoiceQuality = VoiceQuality.NORMAL,
        speakers: List<Speaker> = emptyList(),
        family: VoiceFamily = VoiceFamily.VITS,
    ) = InstalledVoice(
        VoiceManifest(
            schema = 1, id = id, family = family, displayName = id, version = 1, sampleRate = 22050,
            languages = languages, quality = quality, files = VoiceFiles(model = "m.onnx", tokens = "t.txt"),
            espeakData = com.riniso.qvoice.voices.EspeakData.SHARED, speakers = speakers, defaultSpeakerKey = null,
            license = com.riniso.qvoice.voices.LicenseInfo("MIT", "x", ""),
        ),
        File(id), ready = true,
    )

    private fun selector(voices: List<InstalledVoice>, defaults: VoiceDefaults = MapDefaults()) =
        VoiceSelector({ exposeVoices(voices) }, defaults)

    @Test
    fun exposesOneVoicePerSpeakerDefaultFirst() {
        val exposed = exposeVoices(listOf(kitten))
        assertEquals(8, exposed.size)
        assertEquals("kitten-nano-en#rosie", exposed.first().name)
        assertTrue(exposed.all { it.language == "en" && it.region == "US" })
    }

    @Test
    fun singleSpeakerModelUsesBareId() {
        val exposed = exposeVoices(listOf(voice("hi-voice", listOf("hi-IN"))))
        assertEquals("hi-voice", exposed.single().name)
    }

    @Test
    fun findsVoicesByFrameworkName() {
        val s = selector(listOf(kitten))
        assertEquals("Bruno", s.findByName("kitten-nano-en#bruno")?.speaker?.displayName)
        // A bare model id selects the model's default speaker.
        assertEquals("rosie", s.findByName("kitten-nano-en")?.speaker?.key)
        assertNull(s.findByName("kitten-nano-en#nobody"))
        assertNull(s.findByName(""))
        assertNull(s.findByName(null))
    }

    @Test
    fun availabilityUsesFrameworkCodes() {
        val s = selector(listOf(kitten, voice("hi-voice", listOf("hi-IN"))))
        assertEquals(VoiceSelector.Availability.LANGUAGE_AND_REGION, s.availability("eng", "USA"))
        assertEquals(VoiceSelector.Availability.LANGUAGE, s.availability("eng", "IND"))
        assertEquals(VoiceSelector.Availability.LANGUAGE, s.availability("en", null))
        assertEquals(VoiceSelector.Availability.LANGUAGE_AND_REGION, s.availability("hin", "IND"))
        assertEquals(VoiceSelector.Availability.NOT_SUPPORTED, s.availability("tam", "IND"))
        assertEquals(VoiceSelector.Availability.NOT_SUPPORTED, s.availability(null, null))
    }

    @Test
    fun defaultPrefersUserChoiceThenQualityThenDownloadedOverBundled() {
        val better = voice("kokoro", listOf("en-US"), quality = VoiceQuality.VERY_HIGH)
        val defaults = MapDefaults()
        val s = selector(listOf(kitten, better), defaults)
        assertEquals("kokoro", s.defaultFor("eng", "USA")?.name)

        defaults.map["en"] = "kitten-nano-en#leo"
        assertEquals("kitten-nano-en#leo", s.defaultFor("eng", "USA")?.name)

        defaults.map["en-IN"] = "kokoro"
        assertEquals("kokoro", s.defaultFor("eng", "IND")?.name)
        assertEquals("kitten-nano-en#leo", s.defaultFor("eng", "USA")?.name)

        val sameQuality = voice("piper-en", listOf("en-US"))
        assertEquals("piper-en", selector(listOf(kitten, sameQuality)).defaultFor("en", null)?.name)
    }

    /**
     * Galaxy M31: Kokoro (the highest quality) runs at a quarter of real time.
     * As the automatic default it would make every app stutter, so a voice
     * that keeps up wins — but a slow voice still speaks when it's the only
     * one for the language, and a user's choice always wins.
     */
    @Test
    fun automaticDefaultKeepsUpOnThisPhone() {
        val kokoro = voice("kokoro", listOf("en-US", "hi-IN"), quality = VoiceQuality.VERY_HIGH)
        val speeds = mutableMapOf("kokoro" to 0.25f, "kitten-nano-en" to 1.5f)
        val defaults = MapDefaults()
        val s = VoiceSelector({ exposeVoices(listOf(kitten, kokoro)) }, defaults) { speeds[it] }
        assertEquals("kitten-nano-en#rosie", s.defaultFor("eng", "USA")?.name)
        // The only Hindi voice speaks even though it's slow.
        assertEquals("kokoro", s.defaultFor("hin", "IND")?.voice?.id)
        // Chosen by the user: kept.
        defaults.map["en"] = "kokoro@en-US"
        assertEquals("kokoro", s.defaultFor("eng", "USA")?.voice?.id)
        defaults.map.clear()
        // On a faster phone Kokoro keeps up and quality decides again.
        speeds["kokoro"] = 1.4f
        assertEquals("kokoro", s.defaultFor("eng", "USA")?.voice?.id)
        // Unknown speed counts as keeping up (nothing measured yet on a new phone).
        speeds.clear()
        assertEquals("kokoro", s.defaultFor("eng", "USA")?.voice?.id)
    }

    /**
     * Keeping up outranks the region: a British phone gets a fast American
     * voice rather than a British one that pauses after every sentence (the
     * user can still pick the British one).
     */
    @Test
    fun keepingUpOutranksRegion() {
        val slowGb = voice("gb-slow", listOf("en-GB"), quality = VoiceQuality.HIGH)
        val fastUs = voice("us-fast", listOf("en-US"))
        val speeds = mapOf("gb-slow" to 0.4f, "us-fast" to 2.8f)
        val s = VoiceSelector({ exposeVoices(listOf(slowGb, fastUs)) }, MapDefaults()) { speeds[it] }
        assertEquals("us-fast", s.defaultFor("eng", "GBR")?.name)
        // Both keep up: the region decides again.
        val quick = VoiceSelector({ exposeVoices(listOf(slowGb, fastUs)) }, MapDefaults()) { 2f }
        assertEquals("gb-slow", quick.defaultFor("eng", "GBR")?.name)
    }

    @Test
    fun staleUserChoiceIsIgnored() {
        val defaults = MapDefaults(mutableMapOf("en" to "deleted-voice#x"))
        assertEquals("kitten-nano-en#rosie", selector(listOf(kitten), defaults).defaultFor("en", null)?.name)
    }

    @Test
    fun userChoiceInAnotherLanguageIsIgnored() {
        val hindi = voice("hi-voice", listOf("hi-IN"))
        val defaults = MapDefaults(mutableMapOf("en" to "hi-voice"))
        assertEquals("kitten-nano-en#rosie", selector(listOf(kitten, hindi), defaults).defaultFor("en", null)?.name)
    }

    @Test
    fun regionMatchBeatsQuality() {
        val gb = voice("gb", listOf("en-GB"), quality = VoiceQuality.LOW)
        val s = selector(listOf(kitten, gb))
        assertEquals("gb", s.defaultFor("eng", "GBR")?.name)
    }

    @Test
    fun resolveHonoursRequestedVoice() {
        val r = selector(listOf(kitten)).resolve("kitten-nano-en#kiki", "hin", "IND", "hello")
        assertEquals("kitten-nano-en#kiki", (r as VoiceSelector.Resolution.Speak).voice.name)
    }

    @Test
    fun resolveFallsBackToEnglishForLatinText() {
        val r = selector(listOf(kitten)).resolve(null, "tam", "IND", "Hello there")
        assertTrue(r is VoiceSelector.Resolution.Speak)
    }

    @Test
    fun resolveRefusesTamilTextWithoutTamilVoice() {
        val r = selector(listOf(kitten)).resolve(null, "tam", "IND", "வணக்கம் உலகம்")
        assertTrue(r is VoiceSelector.Resolution.Refuse)
    }

    @Test
    fun resolveUsesLanguageVoiceWhenInstalled() {
        val tamil = voice("ta-voice", listOf("ta-IN"))
        val r = selector(listOf(kitten, tamil)).resolve(null, "tam", "IND", "வணக்கம்")
        assertEquals("ta-voice", (r as VoiceSelector.Resolution.Speak).voice.name)
    }

    @Test
    fun resolveWithNoVoicesRefuses() {
        assertTrue(selector(emptyList()).resolve(null, "eng", "USA", "hi") is VoiceSelector.Resolution.Refuse)
    }

    /**
     * A speaker without its own languages speaks all of the model's (right
     * for Supertonic, whose every speaker reads all 31 languages). Kokoro's
     * speakers each have one native language, so its manifest lists them per
     * speaker — otherwise an American voice would be offered for Hindi.
     */
    @Test
    fun speakerLanguageOverridesModelLanguage() {
        val kokoro = voice(
            "kokoro-multi", listOf("en-US", "hi-IN"), quality = VoiceQuality.VERY_HIGH, family = VoiceFamily.KOKORO,
            speakers = listOf(
                Speaker(3, "af_heart", "Heart", Gender.FEMALE, languages = listOf("en-US")),
                Speaker(31, "hf_alpha", "Alpha", Gender.FEMALE, languages = listOf("hi-IN")),
            ),
        )
        val s = selector(listOf(kokoro))
        assertEquals("kokoro-multi#hf_alpha", s.defaultFor("hin", "IND")?.name)
        assertEquals("kokoro-multi#af_heart", s.defaultFor("eng", null)?.name)
        assertEquals(2, exposeVoices(listOf(kokoro)).size)

        val inheriting = kokoro.copy(manifest = kokoro.manifest.copy(speakers = listOf(Speaker(3, "af_heart", "Heart", Gender.FEMALE))))
        assertEquals(listOf("kokoro-multi#af_heart@en-US", "kokoro-multi#af_heart@hi-IN"), exposeVoices(listOf(inheriting)).map { it.name })
    }

    @Test
    fun voiceNamesRoundTrip() {
        assertEquals("a#b", VoiceNames.of("a", "b"))
        assertEquals("a", VoiceNames.of("a", null))
        assertEquals("a#b@hi-IN", VoiceNames.of("a", "b", "hi-IN"))
        assertEquals(VoiceNames.Parts("a", "b", null), VoiceNames.parse("a#b"))
        assertEquals(VoiceNames.Parts("a", null, null), VoiceNames.parse("a"))
        assertEquals(VoiceNames.Parts("a", null, null), VoiceNames.parse("a#"))
        assertEquals(VoiceNames.Parts("a", "b", "hi-IN"), VoiceNames.parse("a#b@hi-IN"))
        assertNull(VoiceNames.parse("#b"))
        assertNull(VoiceNames.parse("  "))
        assertNull(VoiceNames.parse("@hi"))
    }

    @Test
    fun multiLanguageSpeakersGetOneNamePerLanguage() {
        val supertonic = voice(
            "supertonic", listOf("en", "hi", "es"), quality = VoiceQuality.HIGH, family = VoiceFamily.SUPERTONIC,
            speakers = listOf(Speaker(0, "f1", "Female 1", Gender.FEMALE), Speaker(5, "m1", "Male 1", Gender.MALE)),
        )
        val exposed = exposeVoices(listOf(supertonic))
        assertEquals(6, exposed.size)
        assertTrue(exposed.any { it.name == "supertonic#f1@hi" && it.language == "hi" })
        val s = selector(listOf(kitten, supertonic))
        assertEquals("hi", s.findByName("supertonic#m1@hi")?.language)
        // A name without a language picks the speaker's first language.
        assertEquals("en", s.findByName("supertonic#m1")?.language)
        assertEquals("supertonic#f1@hi", s.defaultFor("hin", "IND")?.name)
        // Slice-1/2 names (the int8 build's id) still resolve, to the renamed voice.
        assertEquals("kitten-nano-en#rosie", s.findByName("kitten-nano-en-v0_8-int8#rosie")?.name)
        assertEquals("kitten-nano-en#m1", VoiceNames.of("kitten-nano-en", "m1"))
    }

    /** A default chosen in 0.2.x (saved under the old id) keeps working after the rename. */
    @Test
    fun savedChoiceUnderTheOldBuiltInIdStillApplies() {
        val defaults = MapDefaults(mutableMapOf("en" to "kitten-nano-en-v0_8-int8#leo"))
        assertEquals("kitten-nano-en#leo", selector(listOf(kitten), defaults).defaultFor("eng", "USA")?.name)
        assertEquals("rosie", selector(listOf(kitten)).findByName("kitten-nano-en-v0_8-int8")?.speaker?.key)
        assertEquals("kitten-nano-en", VoiceNames.parse("kitten-nano-en-v0_8-int8#leo@en-US")?.voiceId)
    }

    @Test
    fun speakerQualityOverridesModelQuality() {
        val kokoro = voice(
            "kokoro", listOf("en-US", "hi-IN"), quality = VoiceQuality.VERY_HIGH, family = VoiceFamily.KOKORO,
            speakers = listOf(
                Speaker(3, "af_heart", "Heart", Gender.FEMALE, languages = listOf("en-US")),
                Speaker(31, "hf_alpha", "Alpha", Gender.FEMALE, languages = listOf("hi-IN"), quality = VoiceQuality.NORMAL),
            ),
        )
        val supertonic = voice("supertonic", listOf("hi"), quality = VoiceQuality.HIGH, family = VoiceFamily.SUPERTONIC)
        val s = selector(listOf(kokoro, supertonic))
        assertEquals("supertonic", s.defaultFor("hin", null)?.name)
        assertEquals("kokoro#af_heart", s.defaultFor("eng", null)?.name)
    }
}
