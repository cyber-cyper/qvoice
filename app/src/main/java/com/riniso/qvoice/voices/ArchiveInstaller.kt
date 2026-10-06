package com.riniso.qvoice.voices

import com.riniso.qvoice.catalog.ArchiveFormat
import com.riniso.qvoice.catalog.CatalogEntry
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Installs a downloaded voice archive: checks it is exactly the file the
 * catalogue validated (size, then SHA-256), unpacks it into a staging folder,
 * checks every file the engine will open is there, writes the manifest last
 * and swaps the folder into place.
 *
 * Runs on a worker thread (the install job); a large voice takes a minute on
 * a phone, almost all of it bzip2 decompression, so progress is reported per
 * [Phase] and the work can be cancelled between blocks.
 *
 * Holds [QVoicePaths.installLock] from the first staged byte to the swap, the
 * same rule as the eSpeak NG data copy: start-up cleanup must never delete a
 * staging folder that is being filled (see QVoicePaths.installLock).
 */
class ArchiveInstaller(
    private val paths: QVoicePaths,
    /** Makes sure the shared espeak-ng data exists (BundledVoices.ensureInstalled). */
    private val prepareSharedData: () -> Unit,
    private val freeBytes: (File) -> Long = { it.usableSpace },
) {
    enum class Phase { VERIFYING, EXTRACTING }

    enum class Failure {
        /** Not the file the catalogue describes: re-download. */
        CHECKSUM,
        /** Right checksum but unusable content: the catalogue entry is wrong. */
        CORRUPT,
        NO_SPACE,
        CANCELLED,
        IO,
    }

    class InstallException(val failure: Failure, message: String, cause: Throwable? = null) :
        Exception(message, cause)

    fun interface Progress {
        /** [done] of [total] bytes of the archive processed in [phase]. */
        fun update(phase: Phase, done: Long, total: Long)
    }

    /** @return the manifest as installed (with its source url and checksum). */
    fun install(
        entry: CatalogEntry,
        archive: File,
        progress: Progress = Progress { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): VoiceManifest {
        val d = entry.download
        if (!archive.isFile) throw InstallException(Failure.IO, "download of ${entry.id} is missing")
        if (archive.length() != d.size) {
            throw InstallException(Failure.CHECKSUM, "${entry.id}: ${archive.length()} bytes, expected ${d.size}")
        }
        val actual = sha256(archive, d.size, progress, isCancelled)
        if (actual != d.sha256) {
            throw InstallException(Failure.CHECKSUM, "${entry.id}: sha256 $actual, expected ${d.sha256}")
        }
        if (entry.manifest.espeakData == EspeakData.SHARED) {
            try {
                prepareSharedData()
            } catch (e: IOException) {
                throw classify(e, "shared espeak-ng data")
            }
        }
        synchronized(paths.installLock) {
            val needed = entry.installedSize + SPACE_MARGIN
            paths.filesDir.mkdirs()
            if (freeBytes(paths.filesDir) < needed) {
                throw InstallException(Failure.NO_SPACE, "${entry.id} needs $needed bytes free")
            }
            val staging = paths.newStagingDir(entry.id)
            try {
                // Shared data is not written twice (see EspeakData.SHARED).
                val skip = if (entry.manifest.espeakData == EspeakData.SHARED) listOf("espeak-ng-data/") else emptyList()
                // Headroom over the catalogue's figure only guards against a
                // decompression bomb; the checksum already pins the content.
                val cap = entry.installedSize + entry.installedSize / 10 + 16L * 1024 * 1024
                when (d.format) {
                    ArchiveFormat.TAR_BZ2 -> CountingInputStream(archive.inputStream()) { read ->
                        progress.update(Phase.EXTRACTING, read, d.size)
                    }.use { input ->
                        TarExtractor.extractTarBz2(input, staging, d.root, skip, cap, isCancelled)
                    }
                }
                val missing = entry.manifest.requiredFiles().filterNot { File(staging, it).exists() }
                if (missing.isNotEmpty()) {
                    throw InstallException(Failure.CORRUPT, "${entry.id}: archive lacks ${missing.joinToString()}")
                }
                val manifest = entry.manifest.copy(sourceUrl = d.url, sourceSha256 = d.sha256)
                // Manifest last: its presence is what marks the install complete.
                File(staging, VoiceManifest.FILE_NAME).writeText(VoiceManifestJson.toJson(manifest))
                swapIntoPlace(staging, paths.voiceDir(entry.id), paths.stagingRoot)
                return manifest
            } catch (e: InstallException) {
                staging.deleteRecursively()
                throw e
            } catch (e: IOException) {
                staging.deleteRecursively()
                throw classify(e, entry.id)
            } catch (e: RuntimeException) {
                // commons-compress signals some corrupt input with unchecked exceptions.
                staging.deleteRecursively()
                throw InstallException(Failure.CORRUPT, "${entry.id}: ${e.message}", e)
            }
        }
    }

    private fun sha256(file: File, total: Long, progress: Progress, isCancelled: () -> Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)
        var done = 0L
        file.inputStream().use { input ->
            while (true) {
                if (isCancelled()) throw InstallException(Failure.CANCELLED, "cancelled")
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
                done += n
                progress.update(Phase.VERIFYING, done, total)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * The checksum was verified before extraction, so an unreadable archive
     * here is as unlikely as a disk error, and both mean "install failed" to
     * the user; only running out of space and cancellation need their own
     * handling.
     */
    private fun classify(e: IOException, what: String): InstallException = when {
        e is TarExtractor.CancelledException -> InstallException(Failure.CANCELLED, "cancelled", e)
        // ENOSPC surfaces as an IOException whose message names it.
        e.message.orEmpty().let { it.contains("ENOSPC") || it.contains("No space left", ignoreCase = true) } ->
            InstallException(Failure.NO_SPACE, "$what: ${e.message}", e)
        else -> InstallException(Failure.IO, "$what: ${e.message}", e)
    }

    /** Reports how many bytes of the underlying stream have been read. */
    private class CountingInputStream(input: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(input) {
        private var count = 0L
        private var reported = 0L

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) advance(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) advance(n.toLong())
            return n
        }

        override fun skip(n: Long): Long {
            val skipped = super.skip(n)
            if (skipped > 0) advance(skipped)
            return skipped
        }

        private fun advance(n: Long) {
            count += n
            // Every 256 KiB is plenty for a progress bar.
            if (count - reported >= 256 * 1024) {
                reported = count
                onRead(count)
            }
        }
    }

    companion object {
        /** Free space kept beyond the voice itself, so an install never fills the phone. */
        const val SPACE_MARGIN: Long = 64L * 1024 * 1024
    }
}
