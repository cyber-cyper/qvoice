package com.riniso.qvoice.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** ReaderMemory (what is saved when) and FileMemoryStore (how it reaches the disk). */
class ReaderMemoryTest {

    private class FakeStore : ReaderMemory.Store {
        var content: String? = null
        var writes = 0
        var deletes = 0
        override fun read() = content
        override fun write(text: String) {
            content = text
            writes++
        }
        override fun delete() {
            content = null
            deletes++
        }
    }

    private val store = FakeStore()
    private val memory = ReaderMemory(store)
    private val paragraphs = listOf("One.", "Two.", "Three.")

    private fun state(
        paragraphs: List<String> = this.paragraphs,
        index: Int = 0,
        status: ReadAloud.Status = ReadAloud.Status.READY,
        truncated: Boolean = false,
    ) = ReadAloud.State(paragraphs = paragraphs, index = index, status = status, truncated = truncated)

    private fun saved() = ReaderMemory.decode(store.content!!)!!

    // ---- The file format ----

    @Test
    fun anyTextComesBackExactly() {
        val text = listOf("Hello, world.", "नमस्ते, आप कैसे हैं?", "こんにちは。", "Emoji 😀 and tabs\tstay.", "A — dash, “quotes”.")
        val saved = ReaderMemory.Saved(text, index = 3, truncated = true)
        assertEquals(saved, ReaderMemory.decode(ReaderMemory.encode(saved)))
        assertTrue(ReaderMemory.encode(saved).startsWith(ReaderMemory.HEADER + "\n"))
    }

    @Test
    fun aLineBreakInsideAParagraphNeverSplitsIt() {
        val saved = ReaderMemory.Saved(listOf("Line one\nline two\r\nend."), index = 0, truncated = false)
        assertEquals(listOf("Line one line two  end."), ReaderMemory.decode(ReaderMemory.encode(saved))!!.paragraphs)
    }

    @Test
    fun foreignOrDamagedTextIsRefused() {
        val good = ReaderMemory.encode(ReaderMemory.Saved(paragraphs, index = 1, truncated = false))
        assertEquals(1, ReaderMemory.decode(good)!!.index)
        assertNull(ReaderMemory.decode(""))
        assertNull(ReaderMemory.decode(good.replace(ReaderMemory.HEADER, "QVoice reader 2")))
        assertNull(ReaderMemory.decode(good.replace("index=1", "index=one")))
        assertNull(ReaderMemory.decode(good.replace("index=1", "place=1")))
        assertNull(ReaderMemory.decode(good.replace("truncated=0", "truncated=yes")))
        // Cut off before the first paragraph.
        assertNull(ReaderMemory.decode(ReaderMemory.HEADER + "\nindex=0\ntruncated=0\n"))
        // A place past the end (a shorter text written by hand) is clamped.
        assertEquals(2, ReaderMemory.decode(good.replace("index=1", "index=9"))!!.index)
        assertEquals(0, ReaderMemory.decode(good.replace("index=1", "index=-4"))!!.index)
    }

    // ---- What is saved when ----

    @Test
    fun aNewTextIsSavedAndItsPlaceFollows() {
        memory.onState(state())
        assertEquals(1, store.writes)
        assertEquals(ReaderMemory.Saved(paragraphs, 0, false), saved())
        memory.onState(state(index = 1, status = ReadAloud.Status.PLAYING))
        assertEquals(2, store.writes)
        assertEquals(1, saved().index)
        // Another text (a new list) is saved even at the same place.
        val other = listOf("Other.", "Text.")
        memory.onState(state(paragraphs = other, index = 1, truncated = true))
        assertEquals(3, store.writes)
        assertEquals(ReaderMemory.Saved(other, 1, true), saved())
    }

    @Test
    fun changesThatTouchNeitherTextNorPlaceWriteNothing() {
        val reading = state(index = 1, status = ReadAloud.Status.PLAYING)
        memory.onState(reading)
        memory.onState(reading.copy(status = ReadAloud.Status.PAUSED))
        memory.onState(reading.copy(rate = 2f))
        memory.onState(reading.copy(problem = ReadAloud.Problem.ENGINE))
        memory.onState(reading.copy(sleepAt = 1234L))
        assertEquals(1, store.writes)
    }

    @Test
    fun aFinishedTextIsRememberedFromItsStart() {
        memory.onState(state(index = 2, status = ReadAloud.Status.PLAYING))
        memory.onState(state(index = 2, status = ReadAloud.Status.FINISHED))
        assertEquals(0, saved().index)
    }

    @Test
    fun anEmptiedReaderForgetsTheTextOnce() {
        memory.onState(state())
        memory.onState(state(paragraphs = emptyList(), status = ReadAloud.Status.EMPTY))
        memory.onState(state(paragraphs = emptyList(), status = ReadAloud.Status.EMPTY))
        assertNull(store.content)
        assertEquals(1, store.deletes)
        // Nothing saved yet: an empty reader deletes nothing.
        val fresh = FakeStore()
        ReaderMemory(fresh).onState(state(paragraphs = emptyList(), status = ReadAloud.Status.EMPTY))
        assertEquals(0, fresh.deletes)
    }

    @Test
    fun aRestoredTextIsNotWrittenBackUnchanged() {
        store.content = ReaderMemory.encode(ReaderMemory.Saved(paragraphs, 2, false))
        val restored = memory.restore()!!
        assertEquals(2, restored.index)
        // ReadAloud publishes the restored list itself: no write for it.
        memory.onState(state(paragraphs = restored.paragraphs, index = 2))
        assertEquals(0, store.writes)
        memory.onState(state(paragraphs = restored.paragraphs, index = 0))
        assertEquals(1, store.writes)
    }

    @Test
    fun aDamagedMemoryIsDeletedRatherThanKept() {
        store.content = "not ours"
        assertNull(memory.restore())
        assertEquals(1, store.deletes)
        assertNull(ReaderMemory(FakeStore()).restore())
    }

    // ---- FileMemoryStore ----

    private fun withDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("qvoice-memory").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theFileStoreWritesReadsAndDeletes() = withDir { dir ->
        val file = File(File(dir, "reader"), "last.txt")
        val store = FileMemoryStore(file) { it.run() }
        assertNull(store.read())
        store.write("first")
        store.write("second")
        assertEquals("second", store.read())
        assertFalse(File(file.parentFile, "last.txt.tmp").exists())
        store.delete()
        assertNull(store.read())
        assertFalse(file.exists())
    }

    @Test
    fun aBurstOfChangesCostsOneWriteAndTheLatestWins() = withDir { dir ->
        val file = File(dir, "last.txt")
        val queued = ArrayList<Runnable>()
        val store = FileMemoryStore(file) { queued += it }
        store.write("one")
        store.write("two")
        store.write("three")
        assertEquals(1, queued.size)
        queued.removeAt(0).run()
        assertEquals("three", file.readText())
        // A delete after a write, before it ran: the file ends up gone.
        store.write("four")
        store.delete()
        assertEquals(1, queued.size)
        queued.removeAt(0).run()
        assertFalse(file.exists())
        // A change while a run is under way schedules another run.
        store.write("five")
        queued.removeAt(0).run()
        store.write("six")
        assertEquals(1, queued.size)
        queued.removeAt(0).run()
        assertEquals("six", file.readText())
    }

    @Test
    fun aFailedWriteKeepsThePreviousTextAndThrowsNothing() = withDir { dir ->
        val file = File(dir, "last.txt")
        val store = FileMemoryStore(file) { it.run() }
        store.write("kept")
        // The temporary file's place is taken by a folder: the write fails.
        File(File(dir, "last.txt.tmp"), "blocker").mkdirs()
        store.write("lost")
        assertEquals("kept", store.read())
    }

    @Test
    fun aFileTooBigToBeOursIsNotRead() = withDir { dir ->
        val file = File(dir, "last.txt")
        file.writeBytes(ByteArray((FileMemoryStore.MAX_BYTES + 1).toInt()))
        assertNull(FileMemoryStore(file) { it.run() }.read())
    }
}
