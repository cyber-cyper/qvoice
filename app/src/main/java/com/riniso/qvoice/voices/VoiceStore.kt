package com.riniso.qvoice.voices

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The list of voices QVoice can speak with: the built-in voices first, then
 * the downloaded ones by folder name.
 *
 * For downloads the filesystem is the source of truth: a voice is installed
 * exactly when `voices/<id>/qvoice-voice.json` exists and parses. There is no
 * database to drift out of sync with the files (a class of bug both HayaiTTS
 * and Marmalade had to patch). [voices] returns an in-memory snapshot so the
 * TTS service's binder calls never touch the disk; [refresh] rebuilds it.
 *
 * Built-in voices come from [BundledVoices.declared]; their files are in the
 * APK. They are listed even before the shared eSpeak NG data is copied out
 * ([InstalledVoice.ready] = false). That lets Android's TTS settings and
 * client apps select QVoice immediately after install; [prepare] does the copy
 * on the first synthesis.
 */
class VoiceStore(
    private val paths: QVoicePaths,
    private val bundled: BundledVoices,
) {
    fun interface Listener {
        fun onVoicesChanged(voices: List<InstalledVoice>)
    }

    @Volatile
    private var snapshot: List<InstalledVoice> = emptyList()

    /** Human-readable reasons folders were skipped during the last scan (for logs/diagnostics). */
    @Volatile
    var lastScanProblems: List<String> = emptyList()
        private set

    private val listeners = CopyOnWriteArrayList<Listener>()

    private val firstScan = CountDownLatch(1)

    fun voices(): List<InstalledVoice> = snapshot

    /**
     * Publishes the bundled voices without touching the disk, so the app can
     * start without main-thread I/O while the TTS service still has
     * something to advertise. The real [refresh] runs in the background.
     */
    fun seedDeclared() {
        synchronized(this) {
            if (snapshot.isNotEmpty()) return
            snapshot = bundled.declared.map { builtIn(it, ready = false) }
        }
        for (l in listeners) l.onVoicesChanged(snapshot)
    }

    /**
     * Waits (bounded) for the first disk scan. The TTS service calls this
     * before answering "is this voice valid?" so a downloaded voice isn't
     * reported missing in the few milliseconds after a cold process start.
     * @return true if the scan has completed.
     */
    fun awaitFirstScan(timeoutMillis: Long): Boolean =
        firstScan.await(timeoutMillis, TimeUnit.MILLISECONDS)

    fun find(voiceId: String): InstalledVoice? = snapshot.firstOrNull { it.id == voiceId }

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    /** Re-reads the voices folder and notifies listeners. Cheap: only manifests are read. */
    fun refresh(): List<InstalledVoice> {
        val result = synchronized(this) {
            val problems = ArrayList<String>()
            val builtInReady = bundled.isReady()
            // No duplicates: the scan skips folders named after built-in voices.
            snapshot = bundled.declared.map { builtIn(it, builtInReady) } + scan(problems)
            lastScanProblems = problems
            snapshot
        }
        firstScan.countDown()
        for (l in listeners) l.onVoicesChanged(result)
        return result
    }

    /**
     * Returns [voice] ready to load: a downloaded voice must still be on disk;
     * a built-in voice gets the shared eSpeak NG data copied out of the APK if
     * that hasn't happened yet. Blocks; call from a worker thread (the TTS
     * synthesis thread is fine).
     */
    fun prepare(voice: InstalledVoice): InstalledVoice {
        if (!voice.bundled) {
            if (voice.ready && File(voice.dir, VoiceManifest.FILE_NAME).isFile) return voice
            throw IllegalStateException("voice ${voice.id} is not installed")
        }
        if (voice.ready && bundled.isReady()) return voice
        bundled.ensureInstalled()
        return refresh().firstOrNull { it.id == voice.id && it.ready }
            ?: throw IllegalStateException("voice ${voice.id} still not ready after setup")
    }

    private fun builtIn(voice: BundledVoice, ready: Boolean) =
        InstalledVoice(voice.manifest, paths.voiceDir(voice.manifest.id), ready = ready, assetDir = voice.assetDir)

    private fun scan(problems: MutableList<String>): List<InstalledVoice> {
        val dirs = paths.voicesDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: return emptyList()
        val out = ArrayList<InstalledVoice>()
        for (dir in dirs.sortedBy { it.name }) {
            val manifestFile = File(dir, VoiceManifest.FILE_NAME)
            if (!manifestFile.isFile) {
                problems += "${dir.name}: no manifest (interrupted install)"
                continue
            }
            val manifest = try {
                VoiceManifestJson.parse(manifestFile.readText())
            } catch (e: Exception) {
                problems += "${dir.name}: ${e.message}"
                continue
            }
            if (manifest.id != dir.name) {
                problems += "${dir.name}: manifest id '${manifest.id}' does not match folder"
                continue
            }
            if (bundled.isBundled(manifest.id)) {
                // The copy versions before 1.0.0 unpacked of a voice that is now
                // read from the APK. BundledVoices.ensureInstalled deletes it.
                problems += "${dir.name}: old copy of a built-in voice (deleted at the next start-up)"
                continue
            }
            if (bundled.isRetired(manifest.id)) {
                // An earlier version's built-in voice: listing it would show a
                // second "Kitten Nano". BundledVoices.ensureInstalled deletes it.
                problems += "${dir.name}: retired built-in voice (deleted at the next start-up)"
                continue
            }
            out += InstalledVoice(manifest = manifest, dir = dir, ready = true)
        }
        return out
    }
}
