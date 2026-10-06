package com.riniso.qvoice.voices

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Extracts the .tar.bz2 archives sherpa-onnx publishes its voices in, with
 * the same protections as [ZipExtractor]:
 *
 * - **Path traversal**: every entry must land inside the target folder;
 *   absolute names and ".." are rejected (tar has the same "slip" problem as
 *   zip).
 * - **Links and special files are skipped**, never created: a symlink entry
 *   followed by a file written "through" it is the tar version of zip slip.
 *   sherpa-onnx voice archives contain only plain files and folders.
 * - **Size and entry caps** against corrupt or hostile archives.
 *
 * Plus what the voice installer needs: [root] strips the one top-level folder
 * sherpa-onnx wraps everything in, and [skip] leaves out parts QVoice keeps
 * elsewhere (the 18 MB espeak-ng-data copy in every archive, see
 * EspeakData.SHARED). Uses Apache Commons Compress for bzip2 and tar; Android
 * has neither.
 */
object TarExtractor {

    const val MAX_ENTRIES = 20_000

    class CancelledException : IOException("cancelled")

    /**
     * @param root folder inside the archive to extract from; entries outside
     *   it are an error (the archive isn't the one the catalogue describes).
     * @param skip paths relative to [root] to leave out; a trailing "/" means
     *   the folder and everything in it.
     * @return the number of files written.
     * @throws IOException on unsafe or corrupt archives, oversize content,
     *   cancellation ([CancelledException]) or I/O failure. The caller owns
     *   cleanup of [target] (always a staging folder).
     */
    fun extractTarBz2(
        input: InputStream,
        target: File,
        root: String?,
        skip: List<String> = emptyList(),
        maxTotalBytes: Long = ZipExtractor.DEFAULT_MAX_TOTAL_BYTES,
        isCancelled: () -> Boolean = { false },
    ): Int {
        if (!target.exists() && !target.mkdirs()) throw IOException("cannot create ${target.path}")
        val base = target.canonicalFile
        val basePrefix = base.path + File.separator
        val rootPrefix = root?.trimEnd('/')?.let { "$it/" }
        var files = 0
        var entries = 0
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        TarArchiveInputStream(BZip2CompressorInputStream(input.buffered(64 * 1024))).use { tar ->
            while (true) {
                if (isCancelled()) throw CancelledException()
                val entry = tar.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw IOException("archive has more than $MAX_ENTRIES entries")
                val name = relativeName(entry.name, rootPrefix) ?: continue
                if (skip.any { skipped -> matches(name, skipped) }) continue
                // Checked before isFile: commons-compress reports links as files.
                if (entry.isSymbolicLink || entry.isLink || entry.isCharacterDevice ||
                    entry.isBlockDevice || entry.isFIFO
                ) {
                    continue
                }
                val out = File(base, name).canonicalFile
                if (out.path != base.path && !out.path.startsWith(basePrefix)) {
                    throw IOException("unsafe tar entry '${entry.name}'")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                if (!tar.canReadEntryData(entry)) throw IOException("unsupported tar entry '${entry.name}'")
                out.parentFile?.mkdirs()
                out.outputStream().use { sink ->
                    while (true) {
                        val n = tar.read(buffer)
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

    /**
     * The entry's path below [rootPrefix], or null for the root folder itself.
     * Throws for entries outside the root and for absolute or ".." paths.
     */
    internal fun relativeName(entryName: String, rootPrefix: String?): String? {
        var name = entryName.replace('\\', '/')
        while (name.startsWith("./")) name = name.substring(2)
        if (name.startsWith("/") || name.split('/').any { it == ".." }) {
            throw IOException("unsafe tar entry '$entryName'")
        }
        if (rootPrefix != null) {
            if (name == rootPrefix || name == rootPrefix.dropLast(1)) return null
            if (!name.startsWith(rootPrefix)) throw IOException("tar entry '$entryName' is outside '$rootPrefix'")
            name = name.substring(rootPrefix.length)
        }
        name = name.trimEnd('/')
        return name.ifEmpty { null }
    }

    private fun matches(name: String, skipped: String): Boolean =
        if (skipped.endsWith("/")) name == skipped.dropLast(1) || name.startsWith(skipped) else name == skipped
}
