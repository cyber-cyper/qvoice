package com.riniso.qvoice

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** Shared helpers for tests that need files, assets or archives. */
object TestFiles {

    /**
     * A file from the app module. Gradle runs unit tests with the module
     * folder (app/) as working directory; the sandbox runner uses the project
     * root. Try both.
     */
    fun appFile(relative: String): File =
        listOf(File(relative), File("app", relative)).firstOrNull { it.exists() }
            ?: throw IllegalStateException("cannot find $relative from ${File(".").absolutePath}")

    fun catalogText(): String = appFile("src/main/assets/catalog.json").readText()

    fun tempDir(prefix: String): File = java.nio.file.Files.createTempDirectory(prefix).toFile()

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    sealed class Item(val name: String) {
        class FileItem(name: String, val data: ByteArray) : Item(name)
        class Dir(name: String) : Item(name)
        class Symlink(name: String, val target: String) : Item(name)
    }

    fun file(name: String, data: String) = Item.FileItem(name, data.toByteArray())
    fun file(name: String, size: Int) = Item.FileItem(name, ByteArray(size) { (it % 251).toByte() })

    /** Incompressible content, for archives that must stay large after bzip2. */
    fun randomFile(name: String, size: Int) = Item.FileItem(name, ByteArray(size).also { java.util.Random(42).nextBytes(it) })
    fun dir(name: String) = Item.Dir(name)
    fun symlink(name: String, target: String) = Item.Symlink(name, target)

    /** Builds a .tar.bz2 in memory. Names are written as given (absolute paths and ".." included). */
    fun tarBz2(vararg items: Item): ByteArray {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(bytes)).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            for (item in items) {
                val entry = when (item) {
                    is Item.FileItem -> TarArchiveEntry(item.name, true).apply { size = item.data.size.toLong() }
                    is Item.Dir -> TarArchiveEntry(item.name.trimEnd('/') + "/", true)
                    is Item.Symlink -> TarArchiveEntry(item.name, TarConstants.LF_SYMLINK, true).apply { linkName = item.target }
                }
                tar.putArchiveEntry(entry)
                if (item is Item.FileItem) tar.write(item.data)
                tar.closeArchiveEntry()
            }
        }
        return bytes.toByteArray()
    }
}
