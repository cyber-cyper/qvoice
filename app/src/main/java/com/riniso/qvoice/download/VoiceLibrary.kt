package com.riniso.qvoice.download

import com.riniso.qvoice.catalog.Catalog
import com.riniso.qvoice.catalog.CatalogEntry
import com.riniso.qvoice.engine.EngineHost
import com.riniso.qvoice.voices.ArchiveInstaller
import com.riniso.qvoice.voices.InstalledVoice
import com.riniso.qvoice.voices.QVoicePaths
import com.riniso.qvoice.voices.VoiceManifest
import com.riniso.qvoice.voices.VoiceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** What the voice library shows for one voice pack. */
sealed interface PackState {
    data object Available : PackState
    data class Downloading(val phase: DownloadPhase, val downloaded: Long, val total: Long) : PackState
    data class Installing(val phase: ArchiveInstaller.Phase, val fraction: Float) : PackState
    data class Installed(val updateAvailable: Boolean) : PackState
    data class Failed(val problem: Problem) : PackState
    data object Deleting : PackState
}

/** One voice pack: a catalogue entry, an installed voice, or both (installed from the catalogue). */
data class PackItem(
    val id: String,
    val entry: CatalogEntry?,
    val installed: InstalledVoice?,
    val state: PackState,
) {
    /** What to show: the installed version if there is one. */
    val manifest: VoiceManifest get() = installed?.manifest ?: entry!!.manifest
    val bundled: Boolean get() = installed?.bundled == true
}

/**
 * Downloads, installs and deletes voice packs, and publishes their state for
 * the voice library screen.
 *
 * Nothing here is the source of truth: installed voices come from the
 * filesystem ([VoiceStore]), download progress from DownloadManager
 * ([Downloads]); the library only remembers which download belongs to which
 * voice and the last problem ([LibraryPrefs]). So a killed process, a reboot
 * or a download removed from Android's Downloads app all converge on the
 * next [refresh].
 *
 * The flow: [download] -> DownloadManager -> (broadcast or [refresh] sees it
 * finish) -> [scheduleInstall] (an install job) -> [installDownloaded] on the
 * job's thread -> [VoiceStore.refresh] -> the TTS service sees the voice.
 *
 * Threads: actions come from the UI, installs from the job's thread, refresh
 * from a poller; [lock] guards the in-memory state. Methods that do I/O or
 * IPC say so; call them off the main thread.
 */
class VoiceLibrary(
    private val catalog: () -> Catalog,
    private val store: VoiceStore,
    private val downloads: Downloads,
    private val prefs: LibraryPrefs,
    private val installer: ArchiveInstaller,
    private val engine: EngineHost,
    private val paths: QVoicePaths,
    private val scheduleInstall: (String) -> Unit,
    private val freeBytes: (File) -> Long = { it.usableSpace },
) {
    enum class InstallOutcome { INSTALLED, NOTHING_TO_DO, RETRY_LATER, FAILED }

    private val lock = Any()
    private val installing = HashMap<String, PackState.Installing>()
    private val deleting = HashSet<String>()
    private val scheduled = HashSet<String>()
    private var statuses: Map<String, DownloadProgress> = emptyMap()

    private val _items = MutableStateFlow<List<PackItem>>(emptyList())
    val items: StateFlow<List<PackItem>> = _items.asStateFlow()

    init {
        store.addListener { publish() }
    }

    /** Space a download needs: the archive and the unpacked voice exist side by side while installing. */
    fun bytesNeeded(entry: CatalogEntry): Long =
        entry.download.size + entry.installedSize + ArchiveInstaller.SPACE_MARGIN

    fun freeSpace(): Long = freeBytes(paths.filesDir)

    fun hasSpaceFor(entry: CatalogEntry): Boolean = freeSpace() >= bytesNeeded(entry)

    /**
     * Starts downloading [voiceId] (IPC). [allowMetered] false makes a large
     * download wait for Wi-Fi.
     * @return the problem that prevented it, or null when it started.
     */
    fun download(voiceId: String, allowMetered: Boolean): Problem? {
        val entry = catalog().find(voiceId) ?: return Problem.GONE
        if (downloads.folder() == null) return fail(voiceId, Problem.NO_STORAGE)
        if (!hasSpaceFor(entry)) return fail(voiceId, Problem.NO_SPACE)
        val fileName = "$voiceId.${entry.download.format.key}"
        prefs.downloadId(voiceId)?.let { stale -> runCatching { downloads.remove(stale) } }
        // DownloadManager never overwrites: with a leftover file it would save "name-1".
        downloads.folder()?.let { File(it, fileName).delete() }
        val id = try {
            downloads.enqueue(entry.download.url, fileName, entry.manifest.displayName, allowMetered)
        } catch (e: Exception) {
            return fail(voiceId, Problem.NO_STORAGE)
        }
        synchronized(lock) {
            prefs.setDownloadId(voiceId, id)
            prefs.setProblem(voiceId, null)
            statuses = statuses + (voiceId to DownloadProgress(DownloadPhase.QUEUED, 0, entry.download.size, 0))
        }
        publish()
        return null
    }

    /** Stops a download and deletes what was downloaded so far (IPC). */
    fun cancel(voiceId: String) {
        val id = synchronized(lock) {
            statuses = statuses - voiceId
            prefs.downloadId(voiceId).also { prefs.setDownloadId(voiceId, null) }
        }
        id?.let { runCatching { downloads.remove(it) } }
        publish()
    }

    fun dismissProblem(voiceId: String) {
        prefs.setProblem(voiceId, null)
        publish()
    }

    /**
     * Removes a downloaded voice (blocks: waits for a running synthesis or
     * install). Bundled voices can't be removed.
     *
     * Order matters: the folder is moved away first (atomic; new loads now
     * fail cleanly instead of reading half-deleted files), the voice list is
     * refreshed so nothing selects it, then its loaded model is released —
     * EngineHost waits for a synthesis using it to finish — and only then are
     * the files deleted.
     */
    fun delete(voiceId: String) {
        val voice = store.find(voiceId) ?: return
        if (voice.bundled) return
        synchronized(lock) { deleting += voiceId }
        publish()
        try {
            val trash = synchronized(paths.installLock) {
                val dir = paths.voiceDir(voiceId)
                paths.stagingRoot.mkdirs()
                val aside = File(paths.stagingRoot, "trash-$voiceId-${System.nanoTime()}")
                if (dir.renameTo(aside)) {
                    aside
                } else {
                    dir.deleteRecursively()
                    null
                }
            }
            store.refresh()
            engine.release(voiceId)
            trash?.deleteRecursively()
            prefs.setProblem(voiceId, null)
        } finally {
            synchronized(lock) { deleting -= voiceId }
            publish()
        }
    }

    /**
     * Re-reads every tracked download (IPC): records failures, forgets
     * downloads Android no longer knows, and hands finished ones to the
     * install job. Called by the voice screen's poller, the download-complete
     * broadcast and at app start.
     */
    fun refresh() {
        val tracked = prefs.trackedDownloads()
        val found = if (tracked.isEmpty()) emptyMap() else runCatching { downloads.query(tracked.values) }.getOrDefault(emptyMap())
        val toInstall = ArrayList<String>()
        val toRemove = ArrayList<Long>()
        synchronized(lock) {
            val next = HashMap<String, DownloadProgress>()
            for ((voiceId, id) in tracked) {
                val progress = found[id]
                when {
                    // Removed outside QVoice (Downloads app, cleared storage).
                    progress == null -> prefs.setDownloadId(voiceId, null)
                    progress.phase == DownloadPhase.FAILED -> {
                        prefs.setDownloadId(voiceId, null)
                        prefs.setProblem(voiceId, DownloadStates.problemOf(progress.reason))
                        toRemove += id
                    }
                    progress.phase == DownloadPhase.DONE -> {
                        next[voiceId] = progress
                        if (voiceId !in installing && scheduled.add(voiceId)) toInstall += voiceId
                    }
                    else -> next[voiceId] = progress
                }
            }
            statuses = next
        }
        for (id in toRemove) runCatching { downloads.remove(id) }
        for (voiceId in toInstall) scheduleInstall(voiceId)
        publish()
    }

    /** From the download-complete broadcast (IPC). Ignores downloads that aren't QVoice voices. */
    fun onDownloadComplete(downloadId: Long) {
        if (downloadId in prefs.trackedDownloads().values) refresh()
    }

    /**
     * Installs a finished download (blocks for up to a minute or two; runs on
     * the install job's thread). [isCancelled] turns true when Android stops
     * the job; the download is kept and the job retried later.
     */
    fun installDownloaded(voiceId: String, isCancelled: () -> Boolean = { false }): InstallOutcome {
        try {
            val id = prefs.downloadId(voiceId) ?: return InstallOutcome.NOTHING_TO_DO
            val progress = downloads.query(listOf(id))[id]
            // Failures and vanished downloads are refresh()'s job.
            if (progress?.phase != DownloadPhase.DONE) return InstallOutcome.NOTHING_TO_DO
            val entry = catalog().find(voiceId)
            val file = downloads.localFile(id)
            if (entry == null || file == null) {
                forget(voiceId, id)
                prefs.setProblem(voiceId, Problem.INSTALL)
                return InstallOutcome.FAILED
            }
            synchronized(lock) { installing[voiceId] = PackState.Installing(ArchiveInstaller.Phase.VERIFYING, 0f) }
            publish()
            try {
                installer.install(entry, file, { phase, done, total -> onProgress(voiceId, phase, done, total) }, isCancelled)
            } catch (e: ArchiveInstaller.InstallException) {
                if (e.failure == ArchiveInstaller.Failure.CANCELLED) return InstallOutcome.RETRY_LATER
                forget(voiceId, id)
                prefs.setProblem(
                    voiceId,
                    when (e.failure) {
                        ArchiveInstaller.Failure.CHECKSUM -> Problem.CHECKSUM
                        ArchiveInstaller.Failure.NO_SPACE -> Problem.NO_SPACE
                        else -> Problem.INSTALL
                    },
                )
                return InstallOutcome.FAILED
            } catch (t: Throwable) {
                // Errors too (a missing library method, OutOfMemoryError): the
                // download is dropped either way, otherwise every refresh would
                // schedule the same failing install again.
                forget(voiceId, id)
                prefs.setProblem(voiceId, Problem.INSTALL)
                return InstallOutcome.FAILED
            }
            // An update replaced the files: drop the old version's loaded model.
            engine.release(voiceId)
            forget(voiceId, id)
            prefs.setProblem(voiceId, null)
            store.refresh()
            return InstallOutcome.INSTALLED
        } finally {
            synchronized(lock) {
                installing -= voiceId
                scheduled -= voiceId
            }
            publish()
        }
    }

    /** Rebuilds [items] from memory, the voice store and the last download states. No I/O beyond prefs. */
    fun publish() {
        _items.value = snapshot()
    }

    internal fun snapshot(): List<PackItem> {
        val entries = catalog().entries
        val voices = store.voices()
        synchronized(lock) {
            val out = ArrayList<PackItem>(entries.size + voices.size)
            for (entry in entries) {
                val installed = voices.firstOrNull { it.id == entry.id }
                out += PackItem(entry.id, entry, installed, stateOf(entry, installed))
            }
            val listed = entries.map { it.id }.toSet()
            for (voice in voices) {
                if (voice.id in listed) continue
                // Bundled, or installed from an older catalogue.
                val state = if (voice.id in deleting) PackState.Deleting else PackState.Installed(updateAvailable = false)
                out += PackItem(voice.id, null, voice, state)
            }
            return out
        }
    }

    private fun stateOf(entry: CatalogEntry, installed: InstalledVoice?): PackState {
        val id = entry.id
        if (id in deleting) return PackState.Deleting
        installing[id]?.let { return it }
        statuses[id]?.let { p ->
            return if (p.phase == DownloadPhase.DONE) {
                PackState.Installing(ArchiveInstaller.Phase.VERIFYING, 0f)
            } else {
                PackState.Downloading(p.phase, p.downloaded, if (p.total > 0) p.total else entry.download.size)
            }
        }
        prefs.problem(id)?.let { return PackState.Failed(it) }
        if (installed != null) return PackState.Installed(installed.manifest.version < entry.manifest.version)
        return PackState.Available
    }

    private fun onProgress(voiceId: String, phase: ArchiveInstaller.Phase, done: Long, total: Long) {
        val fraction = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
        val changed = synchronized(lock) {
            val last = installing[voiceId]
            // Whole percents only: a progress bar needs no more, and each
            // publish rebuilds the list.
            if (last != null && last.phase == phase && (fraction * 100).toInt() == (last.fraction * 100).toInt()) {
                false
            } else {
                installing[voiceId] = PackState.Installing(phase, fraction)
                true
            }
        }
        if (changed) publish()
    }

    private fun forget(voiceId: String, downloadId: Long) {
        synchronized(lock) {
            prefs.setDownloadId(voiceId, null)
            statuses = statuses - voiceId
        }
        runCatching { downloads.remove(downloadId) }
    }

    private fun fail(voiceId: String, problem: Problem): Problem {
        prefs.setProblem(voiceId, problem)
        publish()
        return problem
    }
}
