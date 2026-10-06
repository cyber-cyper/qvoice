package com.riniso.qvoice.voices

import com.riniso.qvoice.TestFiles
import com.riniso.qvoice.TestFiles.dir
import com.riniso.qvoice.TestFiles.file
import com.riniso.qvoice.catalog.ArchiveFormat
import com.riniso.qvoice.catalog.CatalogEntry
import com.riniso.qvoice.catalog.DownloadInfo
import com.riniso.qvoice.voices.ArchiveInstaller.Failure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ArchiveInstallerTest {

    private val work = TestFiles.tempDir("qv-install")
    private val paths = QVoicePaths(File(work, "files"))
    private var sharedPrepared = 0
    private var free = Long.MAX_VALUE
    private val installer = ArchiveInstaller(paths, prepareSharedData = { sharedPrepared++ }, freeBytes = { free })

    @After
    fun cleanUp() {
        work.deleteRecursively()
    }

    private val manifest = BundledVoices.KITTEN_NANO_EN.copy(
        id = "demo-voice",
        files = VoiceFiles(model = "model.onnx", tokens = "tokens.txt", voices = "voices.bin"),
        sourceUrl = null,
        sourceSha256 = null,
    )

    private fun archive(model: String = "model-v1") = TestFiles.tarBz2(
        dir("demo-voice/"),
        file("demo-voice/model.onnx", model),
        file("demo-voice/tokens.txt", "t"),
        file("demo-voice/voices.bin", 64),
        file("demo-voice/LICENSE", "Apache-2.0"),
        dir("demo-voice/espeak-ng-data/"),
        file("demo-voice/espeak-ng-data/phontab", 32),
    )

    private fun entryFor(bytes: ByteArray, m: VoiceManifest = manifest) = CatalogEntry(
        manifest = m,
        download = DownloadInfo(
            url = "https://example.com/demo-voice.tar.bz2",
            sha256 = TestFiles.sha256(bytes),
            size = bytes.size.toLong(),
            format = ArchiveFormat.TAR_BZ2,
            root = "demo-voice",
        ),
        installedSize = 1_000,
        summary = "demo",
        acceptance = null,
    )

    private fun download(bytes: ByteArray): File = File(work, "download-${System.nanoTime()}.tar.bz2").apply { writeBytes(bytes) }

    @Test
    fun installsVerifiedArchiveWithManifestLast() {
        val bytes = archive()
        val phases = LinkedHashSet<ArchiveInstaller.Phase>()
        val installed = installer.install(entryFor(bytes), download(bytes), { phase, _, _ -> phases += phase })
        val dir = paths.voiceDir("demo-voice")
        assertEquals("model-v1", File(dir, "model.onnx").readText())
        assertTrue(File(dir, "LICENSE").isFile)
        assertFalse("shared data is not copied again", File(dir, "espeak-ng-data").exists())
        assertEquals(1, sharedPrepared)
        val onDisk = VoiceManifestJson.parse(File(dir, VoiceManifest.FILE_NAME).readText())
        assertEquals(installed, onDisk)
        assertEquals("https://example.com/demo-voice.tar.bz2", onDisk.sourceUrl)
        assertEquals(TestFiles.sha256(bytes), onDisk.sourceSha256)
        assertTrue(ArchiveInstaller.Phase.VERIFYING in phases)
        assertEmptyStaging()
    }

    @Test
    fun rejectsAnyOtherFile() {
        val good = archive()
        val tampered = archive(model = "model-evil")
        expectFailure(Failure.CHECKSUM) { installer.install(entryFor(good), download(tampered)) }
        expectFailure(Failure.CHECKSUM) { installer.install(entryFor(good), download(good.copyOf(good.size - 1))) }
        // Same size, one bit different: only the SHA-256 can tell.
        val flipped = good.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        expectFailure(Failure.CHECKSUM) { installer.install(entryFor(good), download(flipped)) }
        expectFailure(Failure.IO) { installer.install(entryFor(good), File(work, "never-downloaded")) }
        assertFalse(paths.voiceDir("demo-voice").exists())
        assertEquals("nothing was unpacked from an unverified file", 0, sharedPrepared)
    }

    @Test
    fun archiveWithoutAFileTheManifestNamesIsNotInstalled() {
        val bytes = archive()
        val needsLexicon = manifest.copy(files = manifest.files.copy(lexicons = listOf("lexicon.txt")))
        expectFailure(Failure.CORRUPT) { installer.install(entryFor(bytes, needsLexicon), download(bytes)) }
        assertFalse(paths.voiceDir("demo-voice").exists())
        assertEmptyStaging()
    }

    @Test
    fun checksFreeSpaceFirst() {
        free = 10
        val bytes = archive()
        expectFailure(Failure.NO_SPACE) { installer.install(entryFor(bytes), download(bytes)) }
        assertFalse(paths.voiceDir("demo-voice").exists())
    }

    @Test
    fun updateReplacesTheOldVersionAndAFailedUpdateKeepsIt() {
        val v1 = archive("model-v1")
        installer.install(entryFor(v1), download(v1))
        val v2 = archive("model-v2")
        installer.install(entryFor(v2, manifest.copy(version = 2)), download(v2))
        assertEquals("model-v2", File(paths.voiceDir("demo-voice"), "model.onnx").readText())
        assertEmptyStaging()

        // A cancelled update leaves the installed voice exactly as it was.
        val v3 = archive("model-v3")
        expectFailure(Failure.CANCELLED) {
            installer.install(entryFor(v3, manifest.copy(version = 3)), download(v3), isCancelled = { true })
        }
        assertEquals("model-v2", File(paths.voiceDir("demo-voice"), "model.onnx").readText())
        assertEquals(2, VoiceManifestJson.parse(File(paths.voiceDir("demo-voice"), VoiceManifest.FILE_NAME).readText()).version)
        assertEmptyStaging()
    }

    @Test
    fun corruptContentWithTheRightChecksumFailsCleanly() {
        val garbage = "not an archive at all".toByteArray()
        expectFailure(Failure.IO) { installer.install(entryFor(garbage), download(garbage)) }
        assertFalse(paths.voiceDir("demo-voice").exists())
        assertEmptyStaging()
    }

    /**
     * Start-up cleanup (clearStaging) must wait for a running install instead
     * of deleting its half-written folder — the same rule as the bundled copy.
     */
    @Test
    fun startupCleanupWaitsForARunningInstall() {
        // Big enough to report extraction progress several times.
        val bytes = TestFiles.tarBz2(
            TestFiles.randomFile("demo-voice/model.onnx", 3_000_000),
            file("demo-voice/tokens.txt", "t"),
            file("demo-voice/voices.bin", 64),
        )
        val extracting = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cleanupDone = AtomicBoolean(false)
        var failure: Throwable? = null
        val install = Thread {
            try {
                installer.install(entryFor(bytes), download(bytes), { phase, _, _ ->
                    if (phase == ArchiveInstaller.Phase.EXTRACTING && extracting.count > 0) {
                        extracting.countDown()
                        release.await(5, TimeUnit.SECONDS)
                    }
                })
            } catch (t: Throwable) {
                failure = t
            }
        }
        install.start()
        assertTrue(extracting.await(5, TimeUnit.SECONDS))
        val cleanup = Thread {
            paths.clearStaging()
            cleanupDone.set(true)
        }
        cleanup.start()
        Thread.sleep(200)
        assertFalse("cleanup ran during the install", cleanupDone.get())
        release.countDown()
        install.join(10_000)
        cleanup.join(10_000)
        assertEquals(null, failure)
        assertTrue(cleanupDone.get())
        assertEquals(3_000_000L, File(paths.voiceDir("demo-voice"), "model.onnx").length())
    }

    private fun assertEmptyStaging() {
        assertTrue(paths.stagingRoot.listFiles().orEmpty().isEmpty())
    }

    private fun expectFailure(expected: Failure, block: () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (e: ArchiveInstaller.InstallException) {
            assertEquals(e.message, expected, e.failure)
        }
    }
}
