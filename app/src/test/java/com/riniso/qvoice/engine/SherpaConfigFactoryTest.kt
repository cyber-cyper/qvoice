package com.riniso.qvoice.engine

import com.riniso.qvoice.TestFiles
import com.riniso.qvoice.catalog.CatalogJson
import com.riniso.qvoice.voices.AssetSource
import com.riniso.qvoice.voices.BundledVoices
import com.riniso.qvoice.voices.EspeakData
import com.riniso.qvoice.voices.InstalledVoice
import com.riniso.qvoice.voices.VoiceFamily
import com.riniso.qvoice.voices.VoiceManifest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/**
 * The sherpa-onnx configuration built for each voice and language. Uses the
 * real catalogue manifests with empty placeholder files: what matters here is
 * which files are wired into which fields.
 */
class SherpaConfigFactoryTest {

    private val work = TestFiles.tempDir("qv-config")
    private val espeak = File(work, "espeak-ng-data").apply { mkdirs(); File(this, "phontab").writeText("") }
    private val catalog = CatalogJson.parse(TestFiles.catalogText())

    @After
    fun cleanUp() {
        work.deleteRecursively()
    }

    /** A voice folder holding (empty) every file its manifest names. */
    private fun installed(m: VoiceManifest): InstalledVoice {
        val dir = File(work, m.id)
        for (name in m.requiredFiles()) {
            val f = File(dir, name)
            f.parentFile?.mkdirs()
            if (name == m.files.dictDir || m.languageConfig.values.any { it.dictDir == name }) f.mkdirs() else f.writeText("")
        }
        return InstalledVoice(m, dir, ready = true)
    }

    private fun path(voice: InstalledVoice, name: String) = File(voice.dir, name).absolutePath

    @Test
    fun kokoroGetsEachLanguagesOwnSettings() {
        val kokoro = installed(catalog.find("kokoro-multi-lang-v1_0")!!.manifest)

        val en = SherpaConfigFactory.build(kokoro, espeak, 2, "en-US")
        assertEquals("en-us", en.model.kokoro.lang)
        assertEquals(path(kokoro, "lexicon-us-en.txt"), en.model.kokoro.lexicon)
        assertEquals("", en.model.kokoro.dictDir)
        // The Chinese number rules would read "3:45" in Chinese.
        assertEquals("", en.ruleFsts)

        val gb = SherpaConfigFactory.build(kokoro, espeak, 2, "en-GB")
        assertEquals("en", gb.model.kokoro.lang) // espeak-ng has no "en-gb" voice
        assertEquals(path(kokoro, "lexicon-gb-en.txt"), gb.model.kokoro.lexicon)

        val zh = SherpaConfigFactory.build(kokoro, espeak, 2, "zh-CN")
        assertEquals("cmn", zh.model.kokoro.lang)
        assertEquals(listOf("lexicon-us-en.txt", "lexicon-zh.txt").joinToString(",") { path(kokoro, it) }, zh.model.kokoro.lexicon)
        assertEquals(path(kokoro, "dict"), zh.model.kokoro.dictDir)
        assertEquals(3, zh.ruleFsts.split(",").size)

        // Every language a Kokoro speaker is offered in has its settings.
        for (tag in kokoro.manifest.speakers.flatMap { it.languages }.toSet()) {
            val config = SherpaConfigFactory.build(kokoro, espeak, 2, tag)
            assertTrue("$tag: no espeak voice", config.model.kokoro.lang.isNotEmpty())
            assertEquals(espeak.absolutePath, config.model.kokoro.dataDir)
        }
    }

    @Test
    fun everyCatalogueVoiceBuilds() {
        for (entry in catalog.entries) {
            val voice = installed(entry.manifest)
            val config = SherpaConfigFactory.build(voice, espeak, 2, entry.manifest.languages.first())
            when (entry.manifest.family) {
                VoiceFamily.VITS -> {
                    assertEquals(path(voice, entry.manifest.files.model), config.model.vits.model)
                    assertEquals(espeak.absolutePath, config.model.vits.dataDir)
                }
                VoiceFamily.SUPERTONIC -> {
                    assertEquals(path(voice, "vocoder.int8.onnx"), config.model.supertonic.vocoder)
                    assertEquals(path(voice, "voice.bin"), config.model.supertonic.voiceStyle)
                }
                VoiceFamily.KOKORO -> assertEquals(path(voice, "voices.bin"), config.model.kokoro.voices)
                else -> fail("unexpected family in catalogue: ${entry.manifest.family}")
            }
            assertEquals(SherpaConfigFactory.SENTENCES_PER_CHUNK, config.maxNumSentences)
        }
    }

    @Test
    fun missingFilesFailBeforeTheNativeLoad() {
        val kitten = installed(BundledVoices.KITTEN_NANO_EN)
        File(kitten.dir, "voices.bin").delete()
        try {
            SherpaConfigFactory.build(kitten, espeak, 2)
            fail("expected MissingFileException")
        } catch (e: SherpaConfigFactory.MissingFileException) {
            assertTrue(e.message.orEmpty().contains("voices.bin"))
        }
        File(espeak, "phontab").delete()
        File(kitten.dir, "voices.bin").writeText("")
        try {
            SherpaConfigFactory.build(kitten, espeak, 2)
            fail("expected MissingFileException for the shared data")
        } catch (e: SherpaConfigFactory.MissingFileException) {
            assertTrue(e.message.orEmpty().contains("espeak"))
        }
    }

    private val builtIn = BundledVoices.DEFAULT.single()

    /** The built-in voice as it ships: its files in the APK's assets, never on disk. */
    private fun builtInVoice(manifest: VoiceManifest = builtIn.manifest) =
        InstalledVoice(manifest, File(work, "never-read"), ready = true, assetDir = builtIn.assetDir)

    /** Assets holding exactly [names] (paths under assets/). */
    private fun apkWith(names: Collection<String>) = AssetSource { path ->
        if (path in names) ByteArrayInputStream(ByteArray(0)) else throw IOException("no asset $path")
    }

    private fun assetPaths(manifest: VoiceManifest) = manifest.requiredFiles().map { "${builtIn.assetDir}/$it" }

    @Test
    fun builtInVoiceIsReadFromTheApk() {
        val config = SherpaConfigFactory.build(builtInVoice(), espeak, 2, assets = apkWith(assetPaths(builtIn.manifest)))
        assertEquals("${builtIn.assetDir}/${builtIn.manifest.files.model}", config.model.kitten.model)
        assertEquals("${builtIn.assetDir}/voices.bin", config.model.kitten.voices)
        assertEquals("${builtIn.assetDir}/tokens.txt", config.model.kitten.tokens)
        // eSpeak NG still reads a real folder: the shared copy.
        assertEquals(espeak.absolutePath, config.model.kitten.dataDir)
    }

    /**
     * sherpa-onnx ends the whole process (exit -1) when an asset it is told
     * to read is missing, so the load must fail here, before it gets one.
     */
    @Test
    fun missingAssetFailsBeforeTheNativeLoad() {
        val allButVoices = assetPaths(builtIn.manifest).filterNot { it.endsWith("/voices.bin") }
        try {
            SherpaConfigFactory.build(builtInVoice(), espeak, 2, assets = apkWith(allButVoices))
            fail("expected MissingFileException")
        } catch (e: SherpaConfigFactory.MissingFileException) {
            assertTrue(e.message.orEmpty().contains("voices.bin"))
        }
        // Without the assets at all (a wiring mistake), the same.
        try {
            SherpaConfigFactory.build(builtInVoice(), espeak, 2)
            fail("expected MissingFileException without assets")
        } catch (e: SherpaConfigFactory.MissingFileException) {
            assertTrue(e.message.orEmpty().contains("APK"))
        }
    }

    /** eSpeak NG (and the Chinese dictionary) open folders by path; one inside the APK has none. */
    @Test
    fun aVoiceInTheApkCantUseItsOwnFolders() {
        val ownEspeak = builtIn.manifest.copy(espeakData = EspeakData.BUNDLED)
        val assets = apkWith(assetPaths(builtIn.manifest) + "${builtIn.assetDir}/espeak-ng-data")
        try {
            SherpaConfigFactory.build(builtInVoice(ownEspeak), espeak, 2, assets = assets)
            fail("a folder inside the APK was accepted")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("folder"))
        }
    }
}
