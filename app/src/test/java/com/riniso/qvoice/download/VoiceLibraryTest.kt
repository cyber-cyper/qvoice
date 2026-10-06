package com.riniso.qvoice.download

import com.riniso.qvoice.TestFiles
import com.riniso.qvoice.catalog.ArchiveFormat
import com.riniso.qvoice.catalog.Catalog
import com.riniso.qvoice.catalog.CatalogEntry
import com.riniso.qvoice.catalog.DownloadInfo
import com.riniso.qvoice.engine.EngineHost
import com.riniso.qvoice.engine.SpeechModel
import com.riniso.qvoice.voices.ArchiveInstaller
import com.riniso.qvoice.voices.BundledVoice
import com.riniso.qvoice.voices.BundledVoices
import com.riniso.qvoice.voices.QVoicePaths
import com.riniso.qvoice.voices.VoiceFiles
import com.riniso.qvoice.voices.VoiceManifest
import com.riniso.qvoice.voices.VoiceStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceLibraryTest {

    /** DownloadManager stand-in: the test moves downloads through their states. */
    private inner class FakeDownloads : Downloads {
        val states = HashMap<Long, DownloadProgress>()
        val files = HashMap<Long, File>()
        val removed = ArrayList<Long>()
        var nextId = 100L
        var allowMeteredSeen: Boolean? = null

        override fun folder(): File = downloadsDir

        override fun enqueue(url: String, fileName: String, title: String, allowMetered: Boolean): Long {
            allowMeteredSeen = allowMetered
            val id = nextId++
            states[id] = DownloadProgress(DownloadPhase.QUEUED, 0, -1, 0)
            files[id] = File(downloadsDir, fileName)
            return id
        }

        override fun query(ids: Collection<Long>) = ids.mapNotNull { id -> states[id]?.let { id to it } }.toMap()

        override fun localFile(id: Long): File? = files[id]

        override fun remove(id: Long) {
            removed += id
            states.remove(id)
            files.remove(id)?.delete()
        }

        fun finish(id: Long, bytes: ByteArray) {
            files.getValue(id).writeBytes(bytes)
            states[id] = DownloadProgress(DownloadPhase.DONE, bytes.size.toLong(), bytes.size.toLong(), 0)
        }
    }

    private class MapPrefs : LibraryPrefs {
        val ids = HashMap<String, Long>()
        val problems = HashMap<String, Problem>()
        override fun downloadId(voiceId: String) = ids[voiceId]
        override fun setDownloadId(voiceId: String, id: Long?) {
            if (id == null) ids.remove(voiceId) else ids[voiceId] = id
        }
        override fun trackedDownloads(): Map<String, Long> = HashMap(ids)
        override fun problem(voiceId: String) = problems[voiceId]
        override fun setProblem(voiceId: String, problem: Problem?) {
            if (problem == null) problems.remove(voiceId) else problems[voiceId] = problem
        }
    }

    private val work = TestFiles.tempDir("qv-library")
    private val downloadsDir = File(work, "downloads").apply { mkdirs() }
    private val paths = QVoicePaths(File(work, "files"))
    private val fake = FakeDownloads()
    private val prefs = MapPrefs()
    private val scheduled = ArrayList<String>()
    private val released = ArrayList<String>()
    private var free = Long.MAX_VALUE

    private val archive = TestFiles.tarBz2(
        TestFiles.file("demo-voice/model.onnx", "model"),
        TestFiles.file("demo-voice/tokens.txt", "t"),
        TestFiles.file("demo-voice/voices.bin", 8),
    )

    private val manifest = BundledVoices.KITTEN_NANO_EN.copy(
        id = "demo-voice",
        displayName = "Demo",
        files = VoiceFiles(model = "model.onnx", tokens = "tokens.txt", voices = "voices.bin"),
        espeakData = com.riniso.qvoice.voices.EspeakData.NONE,
    )

    private fun entry(m: VoiceManifest = manifest, bytes: ByteArray = archive) = CatalogEntry(
        manifest = m,
        download = DownloadInfo("https://example.com/demo.tar.bz2", TestFiles.sha256(bytes), bytes.size.toLong(), ArchiveFormat.TAR_BZ2, "demo-voice"),
        installedSize = 100,
        summary = "demo",
        acceptance = null,
    )

    private var catalog = Catalog(1, listOf(entry()))

    private fun library(bundled: List<BundledVoice> = emptyList()): Pair<VoiceLibrary, VoiceStore> {
        val store = VoiceStore(paths, BundledVoices({ throw IllegalStateException("no assets") }, paths, bundled))
        store.refresh()
        // A loader that never runs, and a record of which voices were released.
        val engine = object : EngineHost.ModelLoader {
            override fun load(voice: com.riniso.qvoice.voices.InstalledVoice, variant: String?): SpeechModel = error("unused")
        }
        val host = EngineHost(engine)
        val lib = VoiceLibrary(
            catalog = { catalog },
            store = store,
            downloads = fake,
            prefs = prefs,
            installer = ArchiveInstaller(paths, prepareSharedData = {}, freeBytes = { free }),
            engine = host,
            paths = paths,
            scheduleInstall = { scheduled += it },
            freeBytes = { free },
        )
        return lib to store
    }

    @After
    fun cleanUp() {
        work.deleteRecursively()
    }

    private fun VoiceLibrary.state(id: String = "demo-voice") = snapshot().single { it.id == id }.state

    @Test
    fun downloadInstallAndDelete() {
        val (lib, store) = library()
        assertEquals(PackState.Available, lib.state())
        assertNull(lib.download("demo-voice", allowMetered = false))
        assertEquals(false, fake.allowMeteredSeen)
        val id = prefs.ids.getValue("demo-voice")
        assertTrue(lib.state() is PackState.Downloading)

        fake.states[id] = DownloadProgress(DownloadPhase.RUNNING, 50, 200, 0)
        lib.refresh()
        assertEquals(PackState.Downloading(DownloadPhase.RUNNING, 50, 200), lib.state())

        fake.finish(id, archive)
        lib.refresh()
        lib.refresh() // polled again before the job starts: scheduled once only
        assertEquals(listOf("demo-voice"), scheduled)
        assertTrue(lib.state() is PackState.Installing)

        assertEquals(VoiceLibrary.InstallOutcome.INSTALLED, lib.installDownloaded("demo-voice"))
        assertEquals(PackState.Installed(updateAvailable = false), lib.state())
        assertTrue(store.find("demo-voice")!!.ready)
        assertTrue("download file deleted after install", id in fake.removed)
        assertTrue(prefs.ids.isEmpty())
        assertEquals(lib.snapshot(), lib.items.value)

        lib.delete("demo-voice")
        assertEquals(PackState.Available, lib.state())
        assertNull(store.find("demo-voice"))
        assertFalse(paths.voiceDir("demo-voice").exists())
        assertTrue(paths.stagingRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun failedDownloadsAreRecordedAndCleanedUp() {
        val (lib, _) = library()
        lib.download("demo-voice", allowMetered = true)
        val id = prefs.ids.getValue("demo-voice")
        fake.states[id] = DownloadProgress(DownloadPhase.FAILED, 0, -1, 404)
        lib.refresh()
        assertEquals(PackState.Failed(Problem.GONE), lib.state())
        assertTrue(id in fake.removed)
        assertTrue(prefs.ids.isEmpty())
        lib.dismissProblem("demo-voice")
        assertEquals(PackState.Available, lib.state())
    }

    @Test
    fun downloadsRemovedOutsideQVoiceAreForgotten() {
        val (lib, _) = library()
        lib.download("demo-voice", allowMetered = true)
        fake.states.clear() // e.g. cleared in Android's Downloads app
        lib.refresh()
        assertEquals(PackState.Available, lib.state())
        assertTrue(prefs.ids.isEmpty())
    }

    @Test
    fun cancelRemovesTheDownload() {
        val (lib, _) = library()
        lib.download("demo-voice", allowMetered = true)
        val id = prefs.ids.getValue("demo-voice")
        lib.cancel("demo-voice")
        assertEquals(PackState.Available, lib.state())
        assertTrue(id in fake.removed)
    }

    @Test
    fun aTamperedDownloadIsRejectedAndDeleted() {
        val (lib, store) = library()
        lib.download("demo-voice", allowMetered = true)
        val id = prefs.ids.getValue("demo-voice")
        fake.finish(id, archive.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() })
        assertEquals(VoiceLibrary.InstallOutcome.FAILED, lib.installDownloaded("demo-voice"))
        assertEquals(PackState.Failed(Problem.CHECKSUM), lib.state())
        assertNull(store.find("demo-voice"))
        assertTrue(id in fake.removed)
    }

    @Test
    fun anInterruptedInstallKeepsTheDownloadForTheRetry() {
        val (lib, _) = library()
        lib.download("demo-voice", allowMetered = true)
        val id = prefs.ids.getValue("demo-voice")
        fake.finish(id, archive)
        lib.refresh()
        assertEquals(VoiceLibrary.InstallOutcome.RETRY_LATER, lib.installDownloaded("demo-voice") { true })
        assertEquals(id, prefs.ids["demo-voice"])
        assertFalse(id in fake.removed)
        // The retried job installs it.
        assertEquals(VoiceLibrary.InstallOutcome.INSTALLED, lib.installDownloaded("demo-voice"))
        assertEquals(PackState.Installed(false), lib.state())
        // A late duplicate work item finds nothing to do.
        assertEquals(VoiceLibrary.InstallOutcome.NOTHING_TO_DO, lib.installDownloaded("demo-voice"))
    }

    @Test
    fun refusesToStartWithoutSpace() {
        free = 10
        val (lib, _) = library()
        assertEquals(Problem.NO_SPACE, lib.download("demo-voice", allowMetered = true))
        assertTrue(fake.states.isEmpty())
        assertEquals(PackState.Failed(Problem.NO_SPACE), lib.state())
    }

    @Test
    fun newerCatalogueVersionOffersAnUpdate() {
        val (lib, _) = library()
        lib.download("demo-voice", allowMetered = true)
        fake.finish(prefs.ids.getValue("demo-voice"), archive)
        lib.installDownloaded("demo-voice")
        catalog = Catalog(2, listOf(entry(manifest.copy(version = 2))))
        assertEquals(PackState.Installed(updateAvailable = true), lib.state())
    }

    @Test
    fun bundledVoicesAreListedAndNeverDeleted() {
        val (lib, store) = library(bundled = listOf(BundledVoices.DEFAULT.single()))
        val kitten = lib.snapshot().single { it.id == BundledVoices.KITTEN_NANO_EN.id }
        assertTrue(kitten.bundled)
        assertNull(kitten.entry)
        assertEquals(PackState.Installed(false), kitten.state)
        lib.delete(kitten.id)
        assertTrue(store.find(kitten.id) != null)
    }
}
