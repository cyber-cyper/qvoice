package com.riniso.qvoice.voices

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Extracts zip archives with the protections a voice installer needs:
 *
 * - **Zip Slip**: an entry named "../../shared_prefs/x.xml" would otherwise be
 *   written outside the target folder. Every entry's canonical path must stay
 *   inside [extract]'s target.
 * - **Size cap**: a corrupt or hostile archive must not fill the disk; the
 *   total uncompressed size is bounded by [maxTotalBytes].
 *
 * Uses java.util.zip only (part of Android), so it has no dependencies and is
 * unit-tested on the plain JVM.
 */
object ZipExtractor {

    const val DEFAULT_MAX_TOTAL_BYTES: Long = 2L * 1024 * 1024 * 1024 // 2 GB

    /**
     * @return the number of files written.
     * @throws IOException on unsafe entries, oversize content or I/O failure.
     *   The caller owns cleanup of [target] (it is always a staging folder).
     */
    fun extract(
        input: InputStream,
        target: File,
        maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
    ): Int {
        if (!target.exists() && !target.mkdirs()) throw IOException("cannot create ${target.path}")
        val root = target.canonicalFile
        val rootPrefix = root.path + File.separator
        var files = 0
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(root, entry.name).canonicalFile
                if (out.path != root.path && !out.path.startsWith(rootPrefix)) {
                    throw IOException("unsafe zip entry '${entry.name}'")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile?.mkdirs()
                out.outputStream().use { sink ->
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxTotalBytes) throw IOException("archive expands beyond $maxTotalBytes bytes")
                        sink.write(buffer, 0, n)
                    }
                }
                files++
            }
        }
        return files
    }
}
