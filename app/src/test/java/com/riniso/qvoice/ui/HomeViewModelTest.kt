package com.riniso.qvoice.ui

import com.riniso.qvoice.service.exposeVoices
import com.riniso.qvoice.voices.BundledVoices
import com.riniso.qvoice.voices.InstalledVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.Locale

class HomeViewModelTest {

    private val chips = listOf("en-GB", "en-US", "hi-IN", "es").map { LanguageChip(it, it, 1) }

    private fun pick(locale: Locale, defaults: Map<String, String> = emptyMap()) =
        HomeViewModel.preferredLanguage(chips, locale) { language, region ->
            defaults["$language-$region"] ?: defaults[language]
        }

    @Test
    fun ownLanguageAndRegionFirst() {
        assertEquals("hi-IN", pick(Locale.forLanguageTag("hi-IN")))
        assertEquals("en-GB", pick(Locale.forLanguageTag("en-GB")))
    }

    @Test
    fun otherwiseWhereTheLanguagesDefaultVoiceIs() {
        // An Indian English phone: no en-IN voices; apps get the en-US default.
        assertEquals("en-US", pick(Locale.forLanguageTag("en-IN"), mapOf("en" to "en-US")))
        assertEquals("es", pick(Locale.forLanguageTag("es-MX"), mapOf("es" to "es")))
    }

    @Test
    fun otherwiseEnglishThenAnything() {
        assertEquals("en-US", pick(Locale.forLanguageTag("ta-IN"), mapOf("en" to "en-US")))
        assertEquals("en-GB", pick(Locale.forLanguageTag("ta-IN")))
        assertNull(HomeViewModel.preferredLanguage(emptyList(), Locale.US) { _, _ -> null })
    }

    @Test
    fun sampleTextFollowsTheLanguageUntilTheUserTypes() {
        val en = com.riniso.qvoice.service.SampleTexts.forLanguage("en")
        val hi = com.riniso.qvoice.service.SampleTexts.forLanguage("hi")
        assertEquals(hi, HomeViewModel.sampleFor(en, null, "hi-IN")) // first selection
        assertEquals(hi, HomeViewModel.sampleFor(en, "en-US", "hi-IN"))
        assertEquals(en, HomeViewModel.sampleFor(hi, "hi-IN", "en-GB"))
        assertEquals("my own words", HomeViewModel.sampleFor("my own words", "en-US", "hi-IN"))
        assertEquals(hi, HomeViewModel.sampleFor(" ", "en-US", "hi-IN"))
    }

    @Test
    fun threadChoicesFitThePhone() {
        assertEquals(listOf(0, 1, 2, 3, 4, 6, 8), HomeViewModel.threadOptions(8))
        assertEquals(listOf(0, 1, 2, 3, 4, 6), HomeViewModel.threadOptions(6))
        assertEquals(listOf(0, 1, 2, 3, 4), HomeViewModel.threadOptions(4))
        assertEquals(listOf(0, 1), HomeViewModel.threadOptions(1))
        assertEquals(listOf(0, 1, 2, 3, 4, 6, 8), HomeViewModel.threadOptions(12))
    }

    @Test
    fun theReadersVoiceChoiceListsEveryVoiceOfTheLanguage() {
        val kitten = InstalledVoice(BundledVoices.KITTEN_NANO_EN, File("k"), ready = true, assetDir = BundledVoices.DEFAULT.single().assetDir)
        val voices = exposeVoices(listOf(kitten))
        val rows = VoiceRow.listFor("en", voices, current = voices[2].name) { null }
        assertEquals(8, rows.size)
        assertEquals(listOf(voices[2].name), rows.filter { it.isDefault }.map { it.voiceName })
        assertEquals(emptyList<VoiceRow>(), VoiceRow.listFor("hi", voices, current = null) { null })
    }

    @Test
    fun oneChipPerLanguageTagWithCounts() {
        val kitten = InstalledVoice(BundledVoices.KITTEN_NANO_EN, File("k"), ready = true, assetDir = BundledVoices.DEFAULT.single().assetDir)
        val chips = HomeViewModel.languageChips(exposeVoices(listOf(kitten)))
        assertEquals(1, chips.size)
        assertEquals("en-US", chips.single().tag)
        assertEquals(8, chips.single().voiceCount)
    }
}
