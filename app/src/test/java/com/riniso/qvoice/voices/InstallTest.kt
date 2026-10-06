package com.riniso.qvoice.voices

import com.riniso.qvoice.TestFiles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** ZipExtractor, BundledVoices and VoiceStore against a temporary filesDir. */
class InstallTest {

    private val work = TestFiles.tempDir("qv-installtest")
    private var dirs = 0

    @After
    fun cleanUp() {
        work.deleteRecursively()
    }

    /** A fresh folder, deleted with [work] after the test. */
    private fun tempDir(): File = File(work, "d${dirs++}").apply { mkdirs() }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private val espeakZip = zipOf(
        "phontab" to "x", "phonindex" to "x", "phondata" to "x", "intonations" to "x", "en_dict" to "x",
        "lang/gmw/en-US" to "x",
    )

    // Named from the declarations, so the tests follow whichever voice is built in.
    private val kitten = BundledVoices.KITTEN_NANO_EN
    private val builtIn = BundledVoices.DEFAULT.single()

    private fun assets(espeak: ByteArray = espeakZip, log: MutableList<String>? = null) =
        AssetSource { path ->
            log?.add(path)
            when (path) {
                BundledVoices.ESPEAK_ASSET_ZIP -> ByteArrayInputStream(espeak)
                else -> throw IOException("no asset $path")
            }
        }

    @Test
    fun extractorWritesNestedFiles() {
        val dir = tempDir()
        val n = ZipExtractor.extract(ByteArrayInputStream(zipOf("a.txt" to "1", "sub/b.txt" to "2")), dir)
        assertEquals(2, n)
        assertEquals("2", File(dir, "sub/b.txt").readText())
    }

    @Test
    fun extractorRejectsZipSlip() {
        val dir = tempDir()
        try {
            ZipExtractor.extract(ByteArrayInputStream(zipOf("../escape.txt" to "x")), File(dir, "target"))
            fail("zip slip accepted")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("unsafe"))
        }
        assertFalse(File(dir, "escape.txt").exists())
    }

    @Test
    fun extractorEnforcesSizeCap() {
        try {
            ZipExtractor.extract(ByteArrayInputStream(zipOf("big" to "x".repeat(10_000))), tempDir(), maxTotalBytes = 1_000)
            fail("size cap ignored")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("beyond"))
        }
    }

    @Test
    fun builtInSetupCopiesOnlyTheSharedData() {
        val paths = QVoicePaths(tempDir())
        val bundled = BundledVoices(assets(), paths)
        assertFalse(bundled.isReady())
        assertEquals(0L, bundled.ensureInstalled())
        assertTrue(bundled.isReady())

        assertEquals(
            BundledVoices.ESPEAK_DATA_VERSION,
            File(paths.espeakDataDir, BundledVoices.ESPEAK_MARKER).readText(),
        )
        assertTrue(File(paths.espeakDataDir, "lang/gmw/en-US").isFile)
        // The voice itself stays in the APK: nothing of it on disk.
        assertFalse(paths.voiceDir(kitten.id).exists())
        // Staging leftovers are cleaned up after a successful swap.
        assertTrue(paths.stagingRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun builtInSetupIsIdempotent() {
        val paths = QVoicePaths(tempDir())
        val opened = ArrayList<String>()
        val bundled = BundledVoices(assets(log = opened), paths)
        bundled.ensureInstalled()
        assertEquals(listOf(BundledVoices.ESPEAK_ASSET_ZIP), opened)
        bundled.ensureInstalled()
        assertEquals("second call must not re-copy", 1, opened.size)
    }

    @Test
    fun truncatedEspeakDataLeavesNothingHalfInstalled() {
        val paths = QVoicePaths(tempDir())
        val broken = zipOf("phontab" to "x", "phonindex" to "x") // phondata, intonations, en_dict missing
        try {
            BundledVoices(assets(espeak = broken), paths).ensureInstalled()
            fail("broken data accepted")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("lacks"))
        }
        assertFalse(paths.espeakDataDir.exists())
        assertTrue(paths.stagingRoot.listFiles().orEmpty().isEmpty())
    }

    /**
     * Since 1.0.0 the built-in voice is read from the APK. Earlier versions
     * unpacked it to voices/kitten-nano-en/ with a valid manifest: the scan
     * would list that copy as a download (a second "Kitten Nano"), and its
     * 60 MB would stay on the phone for good. It is hidden, then deleted.
     */
    @Test
    fun oldCopyOfTheBuiltInVoiceIsHiddenThenDeleted() {
        val paths = QVoicePaths(tempDir())
        val oldDir = paths.voiceDir(kitten.id).apply { mkdirs() }
        File(oldDir, kitten.files.model).writeBytes(ByteArray(1000))
        File(oldDir, VoiceManifest.FILE_NAME).writeText(VoiceManifestJson.toJson(kitten))
        val bundled = BundledVoices(assets(), paths)
        val store = VoiceStore(paths, bundled)

        val listed = store.refresh().single()
        assertEquals("the APK's voice, not the old copy", builtIn.assetDir, listed.assetDir)
        assertTrue(store.lastScanProblems.single().contains("old copy"))

        assertTrue(bundled.ensureInstalled() >= 1000)
        assertFalse(oldDir.exists())
        assertEquals("nothing left to free", 0L, bundled.ensureInstalled())
    }

    /**
     * Start-up cleanup (paths.clearStaging) runs on a background thread and
     * can overlap with the first synthesis request copying the eSpeak NG
     * data. It must wait for the copy instead of deleting its staging folder.
     * Proven by mutation: without the install lock this test fails.
     */
    @Test
    fun startupCleanupWaitsForARunningInstall() {
        val paths = QVoicePaths(tempDir())
        val firstFileWritten = CountDownLatch(1)
        val cleanupStarted = CountDownLatch(1)
        val slowEspeakZip = object : InputStream() {
            private val data = ByteArrayInputStream(espeakZip)
            override fun read(): Int {
                pauseOnce()
                return data.read()
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                pauseOnce()
                return data.read(b, off, minOf(len, 32)) // small reads: the pause lands mid-archive
            }
            private fun pauseOnce() {
                if (firstFileWritten.count == 0L) return
                val written = paths.stagingRoot.walkTopDown().any { it.name == "phontab" }
                if (written) {
                    firstFileWritten.countDown()
                    cleanupStarted.await(2, TimeUnit.SECONDS)
                    Thread.sleep(200) // a cleanup that ignores the lock would delete right now
                }
            }
        }
        val source = AssetSource { path ->
            if (path == BundledVoices.ESPEAK_ASSET_ZIP) slowEspeakZip else throw IOException("no asset $path")
        }
        var installError: Throwable? = null
        val installer = Thread {
            try {
                BundledVoices(source, paths).ensureInstalled()
            } catch (t: Throwable) {
                installError = t
            }
        }
        installer.start()
        assertTrue(firstFileWritten.await(5, TimeUnit.SECONDS))
        val cleaner = Thread {
            cleanupStarted.countDown()
            paths.clearStaging()
        }
        cleaner.start()
        installer.join(5_000)
        cleaner.join(5_000)
        assertEquals(null, installError)
        assertTrue(File(paths.espeakDataDir, "en_dict").isFile)
    }

    @Test
    fun storeListsBuiltInVoiceBeforeAndAfterSetup() {
        val paths = QVoicePaths(tempDir())
        val store = VoiceStore(paths, BundledVoices(assets(), paths))
        store.seedDeclared()
        val seeded = store.voices().single()
        assertTrue(seeded.bundled)
        assertEquals(builtIn.assetDir, seeded.assetDir)
        assertFalse(seeded.ready)

        val prepared = store.prepare(seeded)
        assertTrue(prepared.ready)
        assertTrue(store.find(kitten.id)!!.ready)
        assertTrue(store.awaitFirstScan(0))
        // Ready: preparing again touches nothing.
        assertSame(prepared, store.prepare(prepared))
    }

    @Test
    fun builtInVoicesComeFirst() {
        val paths = QVoicePaths(tempDir())
        val store = VoiceStore(paths, BundledVoices(assets(), paths))
        val downloaded = kitten.copy(id = "a-downloaded-voice") // sorts before "kitten" by name
        File(paths.voiceDir(downloaded.id).apply { mkdirs() }, VoiceManifest.FILE_NAME)
            .writeText(VoiceManifestJson.toJson(downloaded))

        assertEquals(listOf(kitten.id, downloaded.id), store.refresh().map { it.id })
        assertEquals(listOf(true, false), store.voices().map { it.bundled })
    }

    /**
     * 0.3.0 renamed the built-in voice (int8 build -> fp32 build under a
     * build-neutral id). The old copy has a valid manifest, so without this
     * the scan would list it as a download — a second "Kitten Nano" with the
     * same eight names. The scan skips it; the next setup deletes it.
     */
    @Test
    fun retiredBuiltInVoiceIsHiddenThenDeleted() {
        val paths = QVoicePaths(tempDir())
        val oldId = BundledVoices.RETIRED_IDS.single()
        val old = kitten.copy(id = oldId, files = kitten.files.copy(model = "model.int8.onnx"))
        val oldDir = paths.voiceDir(oldId).apply { mkdirs() }
        File(oldDir, "model.int8.onnx").writeText("m")
        File(oldDir, VoiceManifest.FILE_NAME).writeText(VoiceManifestJson.toJson(old))
        val bundled = BundledVoices(assets(), paths)
        val store = VoiceStore(paths, bundled)

        assertEquals(listOf(kitten.id), store.refresh().map { it.id })
        assertTrue(store.lastScanProblems.single().contains("retired"))
        bundled.ensureInstalled()
        assertFalse(oldDir.exists())
        // A voice that is built in again is never treated as retired.
        assertFalse(BundledVoices(assets(), paths, declared = listOf(BundledVoice(old, "bundled/old"))).isRetired(oldId))
    }

    @Test
    fun storeSkipsBrokenFoldersAndReportsWhy() {
        val paths = QVoicePaths(tempDir())
        val store = VoiceStore(paths, BundledVoices(assets(), paths, declared = emptyList()))
        File(paths.voicesDir, "half-installed").mkdirs()
        File(paths.voicesDir, "bad-json").mkdirs()
        File(paths.voicesDir, "bad-json/${VoiceManifest.FILE_NAME}").writeText("{nope")
        File(paths.voicesDir, "wrong-folder").mkdirs()
        File(paths.voicesDir, "wrong-folder/${VoiceManifest.FILE_NAME}")
            .writeText(VoiceManifestJson.toJson(BundledVoices.KITTEN_NANO_EN))
        val good = BundledVoices.KITTEN_NANO_EN.copy(id = "good-voice")
        File(paths.voicesDir, "good-voice").mkdirs()
        File(paths.voicesDir, "good-voice/${VoiceManifest.FILE_NAME}").writeText(VoiceManifestJson.toJson(good))

        val voices = store.refresh()
        assertEquals(listOf("good-voice"), voices.map { it.id })
        assertTrue(voices.single().ready)
        assertFalse(voices.single().bundled)
        assertEquals(3, store.lastScanProblems.size)
    }

    @Test
    fun preparingAMissingDownloadedVoiceFails() {
        val paths = QVoicePaths(tempDir())
        val store = VoiceStore(paths, BundledVoices(assets(), paths, declared = emptyList()))
        val ghost = InstalledVoice(BundledVoices.KITTEN_NANO_EN.copy(id = "ghost"), File(paths.voicesDir, "ghost"), ready = false)
        try {
            store.prepare(ghost)
            fail("missing voice prepared")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("not installed"))
        }
    }
}
