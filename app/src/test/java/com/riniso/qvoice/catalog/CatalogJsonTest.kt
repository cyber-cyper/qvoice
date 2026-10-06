package com.riniso.qvoice.catalog

import com.riniso.qvoice.TestFiles
import com.riniso.qvoice.engine.SpeedEstimate
import com.riniso.qvoice.service.SampleTexts
import com.riniso.qvoice.service.TtsLocales
import com.riniso.qvoice.service.VoiceDefaults
import com.riniso.qvoice.service.VoiceSelector
import com.riniso.qvoice.service.exposeVoices
import com.riniso.qvoice.voices.BundledVoices
import com.riniso.qvoice.voices.EspeakData
import com.riniso.qvoice.voices.InstalledVoice
import com.riniso.qvoice.voices.VoiceFamily
import com.riniso.qvoice.voices.VoiceManifestJson
import com.riniso.qvoice.voices.VoiceQuality
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Checks the catalogue that ships in the APK (assets/catalog.json, generated
 * by tools/catalog/make_catalog.py from the real archives) and the parser's
 * rejections. A mistake in the catalogue fails here rather than on a phone.
 */
class CatalogJsonTest {

    private val catalog = CatalogJson.parse(TestFiles.catalogText())

    @Test
    fun shippedCatalogueParsesAndIsConsistent() {
        assertTrue(catalog.entries.size >= 7)
        val bundledIds = BundledVoices.DEFAULT.map { it.manifest.id }.toSet()
        for (e in catalog.entries) {
            val m = e.manifest
            assertTrue("${e.id}: bundled voices are not downloadable", e.id !in bundledIds)
            assertTrue("${e.id}: url", e.download.url == CatalogJsonTestData.BASE + e.id + ".tar.bz2")
            assertEquals("${e.id}: archive folder is the voice id", e.id, e.download.root)
            assertTrue("${e.id}: installed size", e.installedSize > e.download.size / 2)
            assertNotNull("${e.id}: licence text", Licences.textAssetFor(m.license.name))
            assertTrue("${e.id}: licence file", TestFiles.appFile("src/main/assets/" + Licences.textAssetFor(m.license.name)).isFile)
            assertTrue("${e.id}: speakers", m.speakers.isNotEmpty())
            assertEquals("${e.id}: speaker ids unique", m.speakers.size, m.speakers.map { it.sid }.toSet().size)
            assertTrue("${e.id}: default speaker", m.speakers.any { it.key == m.defaultSpeakerKey })
            for (s in m.speakers) {
                for (tag in m.languagesOf(s)) assertTrue("${e.id}#${s.key}: $tag not a model language", tag in m.languages)
            }
            if (m.family == VoiceFamily.SUPERTONIC) {
                assertEquals(EspeakData.NONE, m.espeakData)
            } else {
                // Every espeak-based archive was checked byte-identical to the shared copy.
                assertEquals("${e.id}: espeak", EspeakData.SHARED, m.espeakData)
            }
            // The manifest round-trips (it is written to disk as-is after install).
            assertEquals(m, VoiceManifestJson.parse(VoiceManifestJson.toJson(m)))
        }
    }

    @Test
    fun kokoroSpeaksOneLanguageEachWithALoadConfigForIt() {
        val kokoro = catalog.find("kokoro-multi-lang-v1_0")!!.manifest
        assertEquals("af_heart", kokoro.defaultSpeakerKey)
        assertEquals(kokoro.speakers.first().key, kokoro.defaultSpeakerKey)
        for (s in kokoro.speakers) {
            assertEquals("${s.key} must list exactly one language", 1, s.languages.size)
            val config = kokoro.configFor(s.languages.single())
            assertNotNull("${s.key}: no load config for ${s.languages}", config?.espeakVoice)
            assertNotNull("${s.key}: quality set explicitly", s.quality)
        }
        // Japanese is excluded (espeak-ng can't read kanji), so is zf_xiaoni (garbled).
        assertTrue(kokoro.speakers.none { it.key.startsWith("j") || it.key == "zf_xiaoni" })
        // Chinese number rules only for Chinese: on English they read digits in Chinese.
        for ((tag, config) in kokoro.languageConfig) {
            assertEquals(tag, tag.startsWith("zh"), config.ruleFsts.orEmpty().isNotEmpty())
        }
        assertTrue(kokoro.files.ruleFsts.isEmpty())
        assertEquals(VoiceQuality.VERY_HIGH, kokoro.speakers.first { it.key == "af_heart" }.quality)
        assertEquals(VoiceQuality.NORMAL, kokoro.speakers.first { it.key == "hf_alpha" }.quality)
    }

    @Test
    fun supertonicNeedsItsLicenceAccepted() {
        val supertonic = catalog.entries.single { it.manifest.family == VoiceFamily.SUPERTONIC }
        assertEquals(Licences.OPENRAIL_M, supertonic.acceptance)
        assertEquals(31, supertonic.manifest.languages.size)
        assertEquals(
            setOf("duration_predictor", "text_encoder", "vector_estimator", "vocoder", "tts_json", "unicode_indexer", "voice_style"),
            supertonic.manifest.files.extra.keys,
        )
        assertTrue(catalog.entries.filter { it !== supertonic }.all { it.acceptance == null })
    }

    /** What an app asking for a language gets, once the catalogue voices are installed. */
    @Test
    fun defaultsWithEverythingInstalled() {
        val kitten = InstalledVoice(BundledVoices.KITTEN_NANO_EN, File("k"), ready = true, assetDir = BundledVoices.DEFAULT.single().assetDir)
        val downloaded = catalog.entries.map { InstalledVoice(it.manifest, File(it.id), ready = true) }
        val all = listOf(kitten) + downloaded
        val selector = VoiceSelector({ exposeVoices(all) }, object : VoiceDefaults {
            override fun defaultVoiceFor(languageKey: String): String? = null
        })
        assertEquals("kokoro-multi-lang-v1_0#af_heart", selector.defaultFor("eng", "USA")?.name)
        assertEquals("kokoro-multi-lang-v1_0#bf_emma", selector.defaultFor("eng", "GBR")?.name)
        // Hindi: Supertonic (HIGH) over Kokoro's C-grade Hindi voices, both tagged hi-IN.
        assertEquals("sherpa-onnx-supertonic-3-tts-int8-2026-05-11#f1@hi-IN", selector.defaultFor("hin", "IND")?.name)
        assertEquals("sherpa-onnx-supertonic-3-tts-int8-2026-05-11#f1@ko-KR", selector.defaultFor("kor", "KOR")?.name)
        assertEquals(VoiceSelector.Availability.NOT_SUPPORTED, selector.availability("tam", "IND"))

        // Only the bundled voice plus one Piper voice: the download wins a tie.
        val piper = downloaded.single { it.id == "vits-piper-en_US-kristin-medium" }
        val small = VoiceSelector({ exposeVoices(listOf(kitten, piper)) }, object : VoiceDefaults {
            override fun defaultVoiceFor(languageKey: String): String? = null
        })
        assertEquals("vits-piper-en_US-kristin-medium#kristin", small.defaultFor("eng", "USA")?.name)
    }

    /**
     * Relative speeds from the Galaxy M31 logs (built-in voice = 1.0): Piper
     * much faster, Kokoro much slower, Supertonic estimated in between.
     */
    @Test
    fun everyVoiceHasAPlausibleSpeedHint() {
        for (e in catalog.entries) {
            val hint = e.speedHint
            assertNotNull("${e.id}: speedHint", hint)
            when (e.manifest.family) {
                VoiceFamily.VITS -> assertTrue("${e.id}: Piper medium is faster than the built-in voice", hint!! > 1.2f)
                VoiceFamily.KOKORO -> assertTrue("${e.id}: Kokoro is far slower", hint!! < 0.5f)
                else -> assertTrue("${e.id}: ${hint}", hint!! in 0.05f..5f)
            }
        }
    }

    /** What a Galaxy M31 (built-in voice measured at 1.5x) gets as automatic defaults. */
    @Test
    fun defaultsOnASlowPhoneKeepUp() {
        val kitten = InstalledVoice(BundledVoices.KITTEN_NANO_EN, File("k"), ready = true, assetDir = BundledVoices.DEFAULT.single().assetDir)
        val downloaded = catalog.entries.map { InstalledVoice(it.manifest, File(it.id), ready = true) }
        val measured = mapOf(BundledVoices.KITTEN_NANO_EN.id to 1.5f)
        val speedOf = { id: String ->
            SpeedEstimate.of(id, BundledVoices.KITTEN_NANO_EN.id, { measured[it] }, { catalog.find(it)?.speedHint })?.factor
        }
        val selector = VoiceSelector({ exposeVoices(listOf(kitten) + downloaded) }, object : VoiceDefaults {
            override fun defaultVoiceFor(languageKey: String): String? = null
        }, speedOf)
        // English: a fast Piper voice, not Kokoro (0.26x there).
        assertEquals(VoiceFamily.VITS, selector.defaultFor("eng", "USA")?.voice?.manifest?.family)
        assertEquals("vits-piper-en_GB-cori-medium#cori", selector.defaultFor("eng", "GBR")?.name)
        // Hindi has only slow voices on that phone: the better one still speaks.
        assertEquals("sherpa-onnx-supertonic-3-tts-int8-2026-05-11#f1@hi-IN", selector.defaultFor("hin", "IND")?.name)
    }

    @Test
    fun everyCatalogueLanguageHasASampleSentence() {
        val languages = catalog.entries.flatMap { it.manifest.languages }.map { TtsLocales.parseTag(it)!!.first }.toSet()
        val missing = languages - SampleTexts.languages
        assertTrue("no sample sentence for $missing", missing.isEmpty())
    }

    @Test
    fun rejectsBadEntries() {
        fun mutated(change: (JSONObject) -> Unit): String {
            val root = JSONObject(TestFiles.catalogText())
            change(root.getJSONArray("voices").getJSONObject(0))
            return root.toString()
        }
        expectInvalid(mutated { it.getJSONObject("download").put("url", "http://example.com/x.tar.bz2") }, "https")
        expectInvalid(mutated { it.getJSONObject("download").put("sha256", "ABC") }, "sha256")
        expectInvalid(mutated { it.getJSONObject("download").put("format", "rar") }, "format")
        expectInvalid(mutated { it.getJSONObject("download").put("size", 0) }, "size")
        expectInvalid(mutated { it.getJSONObject("download").put("root", "../x") }, "unsafe")
        expectInvalid(mutated { it.put("acceptance", "sign-in-blood") }, "licence")
        expectInvalid(mutated { it.put("installedSize", -1) }, "installedSize")
        expectInvalid(mutated { it.getJSONObject("manifest").put("family", "mystery") }, "unknown family")
        expectInvalid(mutated { it.put("speedHint", 0) }, "speedHint")
        expectInvalid(mutated { it.put("speedHint", 99) }, "speedHint")
        expectInvalid(mutated { it.put("speedHint", "fast") }, "speedHint")
        // Optional: an older catalogue without hints still parses.
        val withoutHint = CatalogJson.parse(mutated { it.remove("speedHint") })
        assertEquals(null, withoutHint.entries.first().speedHint)
        val root = JSONObject(TestFiles.catalogText())
        root.getJSONArray("voices").put(JSONObject(root.getJSONArray("voices").getJSONObject(0).toString()))
        expectInvalid(root.toString(), "duplicate")
        expectInvalid("""{"schema": 2, "version": 1, "voices": []}""", "newer")
    }

    private fun expectInvalid(json: String, messagePart: String) {
        try {
            CatalogJson.parse(json)
            fail("expected InvalidCatalogException containing '$messagePart'")
        } catch (e: CatalogJson.InvalidCatalogException) {
            assertTrue("message was: ${e.message}", e.message.orEmpty().contains(messagePart))
        }
    }
}

private object CatalogJsonTestData {
    const val BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
}
