package com.riniso.qvoice.voices

import com.riniso.qvoice.TestFiles
import com.riniso.qvoice.TestFiles.dir
import com.riniso.qvoice.TestFiles.file
import com.riniso.qvoice.TestFiles.symlink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class TarExtractorTest {

    private val work = TestFiles.tempDir("qv-tar")
    private val target = File(work, "out")

    @After
    fun cleanUp() {
        work.deleteRecursively()
    }

    private fun extract(archive: ByteArray, root: String?, skip: List<String> = emptyList(), max: Long = 1L shl 30) =
        TarExtractor.extractTarBz2(archive.inputStream(), target, root, skip, max)

    @Test
    fun stripsTheRootFolderAndSkipsSharedData() {
        val archive = TestFiles.tarBz2(
            dir("voice/"),
            file("voice/model.onnx", 100),
            dir("voice/espeak-ng-data/"),
            file("voice/espeak-ng-data/phontab", 10),
            dir("voice/dict/"),
            file("voice/dict/jieba.dict.utf8", "x"),
            file("voice/espeak-ng-data-not-really.txt", "kept: only the folder is skipped"),
        )
        assertEquals(3, extract(archive, "voice", listOf("espeak-ng-data/")))
        assertEquals(100L, File(target, "model.onnx").length())
        assertTrue(File(target, "dict/jieba.dict.utf8").isFile)
        assertTrue(File(target, "espeak-ng-data-not-really.txt").isFile)
        assertFalse(File(target, "espeak-ng-data").exists())
    }

    @Test
    fun rejectsPathTraversal() {
        expectFailure(TestFiles.tarBz2(file("voice/../../evil.txt", "x")), "voice", "unsafe")
        expectFailure(TestFiles.tarBz2(file("/etc/evil.txt", "x")), null, "unsafe")
        assertFalse(File(work, "evil.txt").exists())
    }

    @Test
    fun rejectsEntriesOutsideTheExpectedFolder() {
        expectFailure(TestFiles.tarBz2(file("voice/model.onnx", "x"), file("other/model.onnx", "x")), "voice", "outside")
    }

    /**
     * The tar version of zip slip: a symlink pointing out of the folder, then
     * a file written "through" it. Links are never created, so the second
     * entry becomes an ordinary file inside a real folder.
     */
    @Test
    fun neverCreatesLinks() {
        val outside = File(work, "outside").apply { mkdirs() }
        val archive = TestFiles.tarBz2(
            symlink("voice/escape", outside.absolutePath),
            file("voice/escape/planted.txt", "x"),
            file("voice/model.onnx", "m"),
        )
        extract(archive, "voice")
        assertFalse(Files.isSymbolicLink(File(target, "escape").toPath()))
        assertTrue(File(target, "escape/planted.txt").isFile)
        assertFalse(File(outside, "planted.txt").exists())
    }

    @Test
    fun capsTheExpandedSize() {
        expectFailure(TestFiles.tarBz2(file("voice/big.bin", 10_000)), "voice", "expands beyond", max = 5_000)
    }

    @Test
    fun stopsWhenCancelled() {
        val archive = TestFiles.tarBz2(file("voice/a", 10), file("voice/b", 10))
        var calls = 0
        try {
            TarExtractor.extractTarBz2(archive.inputStream(), target, "voice", isCancelled = { ++calls > 1 })
            fail("expected cancellation")
        } catch (e: TarExtractor.CancelledException) {
            assertEquals(2, calls)
        }
    }

    @Test
    fun rejectsSomethingThatIsNotBzip2() {
        try {
            extract("definitely not bzip2".toByteArray(), null)
            fail("expected IOException")
        } catch (e: IOException) {
            // commons-compress: "Stream is not in the BZip2 format"
        }
    }

    @Test
    fun relativeNames() {
        assertNull(TarExtractor.relativeName("voice/", "voice/"))
        assertNull(TarExtractor.relativeName("voice", "voice/"))
        assertNull(TarExtractor.relativeName("./voice/", "voice/"))
        assertEquals("model.onnx", TarExtractor.relativeName("./voice/model.onnx", "voice/"))
        assertEquals("dict", TarExtractor.relativeName("voice/dict/", "voice/"))
        assertEquals("a/b", TarExtractor.relativeName("a/b", null))
        try {
            TarExtractor.relativeName("voice-2/x", "voice/")
            fail("a sibling folder sharing the prefix is outside the root")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("outside"))
        }
    }

    private fun expectFailure(archive: ByteArray, root: String?, messagePart: String, max: Long = 1L shl 30) {
        try {
            extract(archive, root, max = max)
            fail("expected IOException containing '$messagePart'")
        } catch (e: IOException) {
            assertTrue("message was: ${e.message}", e.message.orEmpty().contains(messagePart))
        }
    }
}
