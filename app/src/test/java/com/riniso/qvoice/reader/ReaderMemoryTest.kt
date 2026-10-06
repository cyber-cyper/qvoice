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
        var text: String? = null
        var place: String? = null
        /** Every write: whether it carried the text, and the place. */
        val writes = ArrayList<Pair<Boolean, String>>()
        var deletes = 0
        override fun read() = ReaderMemory.Parts(text, place)
        override fun write(text: String?, place: String) {
            if (text != null) this.text = text
            this.place = place
            writes += (text != null) to place
        }
        override fun delete() {
            text = null
            place = null
            deletes++
        }
    }

    private var nextId = 100L
    private val store = FakeStore()
    private val memory = ReaderMemory(store) { nextId++ }
    private val paragraphs = listOf("One.", "Two.", "Three.")

    private fun state(
        paragraphs: List<String> = this.paragraphs,
        index: Int = 0,
        status: ReadAloud.Status = ReadAloud.Status.READY,
        truncated: Boolean = false,
        sentence: Int = 0,
    ) = ReadAloud.State(paragraphs = paragraphs, index = index, sentence = sentence, status = status, truncated = truncated)

    /** What a fresh ReaderMemory would restore from the store as it is now. */
    private fun saved() = ReaderMemory(store) { 999L }.restore()!!

    // ---- The two parts ----

    @Test
    fun anyTextComesBackExactly() {
        val text = listOf("Hello, world.", "नमस्ते, आप कैसे हैं?", "こんにちは。", "Emoji 😀 and tabs\tstay.", "A — dash, “quotes”.")
        val decoded = ReaderMemory.decodeText(ReaderMemory.encodeText(42L, text, truncated = true))!!
        assertEquals(42L, decoded.id)
        assertEquals(text, decoded.paragraphs)
        assertTrue(decoded.truncated)
        assertNull(decoded.legacyPlace)
        assertTrue(ReaderMemory.encodeText(42L, text, true).startsWith(ReaderMemory.HEADER + "\n"))
    }

    @Test
    fun aLineBreakInsideAParagraphNeverSplitsIt() {
        val decoded = ReaderMemory.decodeText(ReaderMemory.encodeText(1L, listOf("Line one\nline two\r\nend."), false))!!
        assertEquals(listOf("Line one line two  end."), decoded.paragraphs)
    }

    @Test
    fun foreignOrDamagedTextIsRefused() {
        val good = ReaderMemory.encodeText(7L, paragraphs, truncated = false)
        assertEquals(7L, ReaderMemory.decodeText(good)!!.id)
        assertNull(ReaderMemory.decodeText(""))
        assertNull(ReaderMemory.decodeText(good.replace(ReaderMemory.HEADER, "QVoice reader 3")))
        assertNull(ReaderMemory.decodeText(good.replace("id=7", "id=seven")))
        assertNull(ReaderMemory.decodeText(good.replace("id=7", "index=7")))
        assertNull(ReaderMemory.decodeText(good.replace("truncated=0", "truncated=yes")))
        // Cut off before the first paragraph.
        assertNull(ReaderMemory.decodeText(ReaderMemory.HEADER + "\nid=7\ntruncated=0\n"))
    }

    @Test
    fun aPlaceCountsOnlyForItsOwnText() {
        assertEquals(2 to 3, ReaderMemory.decodePlace(ReaderMemory.encodePlace(7L, 2 to 3), 7L))
        assertNull("another text's place", ReaderMemory.decodePlace(ReaderMemory.encodePlace(6L, 2 to 3), 7L))
        assertNull(ReaderMemory.decodePlace(null, 7L))
        assertNull(ReaderMemory.decodePlace("7 two 3", 7L))
        assertNull(ReaderMemory.decodePlace("7 2", 7L))
    }

    @Test
    fun aFileFromTheVersionBeforeSentencesIsStillRead() {
        // Slices 18 to 23 wrote version 1: one file, the paragraph inside it.
        val v1 = ReaderMemory.HEADER_V1 + "\nindex=2\ntruncated=1\nOne.\nTwo.\nThree.\n"
        val decoded = ReaderMemory.decodeText(v1)!!
        assertEquals(2 to 0, decoded.legacyPlace)
        assertEquals(paragraphs, decoded.paragraphs)
        assertTrue(decoded.truncated)
    }

    // ---- What is saved when ----

    @Test
    fun aNewTextIsSavedWithItsPlaceAndThenOnlyThePlace() {
        memory.onState(state())
        assertEquals(listOf(true to "100 0 0"), store.writes)
        memory.onState(state(index = 1, status = ReadAloud.Status.PLAYING))
        // The text, hundreds of kilobytes, isn't written again for a new place.
        assertEquals(false to "100 1 0", store.writes.last())
        assertEquals(ReaderMemory.Saved(paragraphs, 1, 0, false), saved())
        // Another text (a new list) gets a new id and is saved, even at the same place.
        val other = listOf("Other.", "Text.")
        memory.onState(state(paragraphs = other, index = 1, truncated = true))
        assertEquals(true to "101 1 0", store.writes.last())
        assertEquals(ReaderMemory.Saved(other, 1, 0, true), saved())
    }

    @Test
    fun theSentenceIsPartOfThePlace() {
        memory.onState(state(index = 1, status = ReadAloud.Status.PLAYING))
        memory.onState(state(index = 1, status = ReadAloud.Status.PLAYING, sentence = 2))
        assertEquals(2, store.writes.size)
        assertEquals(1 to 2, saved().let { it.index to it.sentence })
        // A finished text starts over from its first sentence, as Play does.
        memory.onState(state(index = 1, status = ReadAloud.Status.FINISHED, sentence = 2))
        assertEquals(0 to 0, saved().let { it.index to it.sentence })
    }

    @Test
    fun changesThatTouchNeitherTextNorPlaceWriteNothing() {
        val reading = state(index = 1, status = ReadAloud.Status.PLAYING)
        memory.onState(reading)
        memory.onState(reading.copy(status = ReadAloud.Status.PAUSED))
        memory.onState(reading.copy(rate = 2f))
        memory.onState(reading.copy(problem = ReadAloud.Problem.ENGINE))
        memory.onState(reading.copy(sleepAt = 1234L))
        memory.onState(reading.copy(preparing = true))
        assertEquals(1, store.writes.size)
    }

    @Test
    fun anEmptiedReaderForgetsTheTextOnce() {
        memory.onState(state())
        memory.onState(state(paragraphs = emptyList(), status = ReadAloud.Status.EMPTY))
        memory.onState(state(paragraphs = emptyList(), status = ReadAloud.Status.EMPTY))
        assertNull(store.text)
        assertEquals(1, store.deletes)
        // Nothing saved yet: an empty reader deletes nothing.
        val fresh = FakeStore()
        ReaderMemory(fresh).onState(state(paragraphs = emptyList(), status = ReadAloud.Status.EMPTY))
        assertEquals(0, fresh.deletes)
    }

    @Test
    fun aRestoredTextIsNotWrittenBackAndItsPlaceKeepsItsId() {
        store.text = ReaderMemory.encodeText(55L, paragraphs, false)
        store.place = ReaderMemory.encodePlace(55L, 2 to 1)
        val restored = memory.restore()!!
        assertEquals(2 to 1, restored.index to restored.sentence)
        // ReadAloud publishes the restored list itself: no write for it.
        memory.onState(state(paragraphs = restored.paragraphs, index = 2, sentence = 1))
        assertTrue(store.writes.isEmpty())
        memory.onState(state(paragraphs = restored.paragraphs, index = 0))
        assertEquals(listOf(false to "55 0 0"), store.writes)
    }

    @Test
    fun aPlaceOfAnotherTextStartsAtTheTop() {
        // The phone died between writing a new text and its place.
        store.text = ReaderMemory.encodeText(56L, paragraphs, false)
        store.place = ReaderMemory.encodePlace(55L, 2 to 1)
        assertEquals(0 to 0, memory.restore()!!.let { it.index to it.sentence })
    }

    @Test
    fun aVersionOneFileIsRewrittenInTwoPartsAtOnce() {
        store.text = ReaderMemory.HEADER_V1 + "\nindex=2\ntruncated=0\nOne.\nTwo.\nThree.\n"
        val restored = memory.restore()!!
        assertEquals(2 to 0, restored.index to restored.sentence)
        assertEquals(listOf(true to "100 2 0"), store.writes)
        assertEquals(100L, ReaderMemory.decodeText(store.text!!)!!.id)
        // From then on a place change is a place write, and it counts.
        memory.onState(state(paragraphs = restored.paragraphs, index = 1))
        assertEquals(1 to 0, saved().let { it.index to it.sentence })
    }

    @Test
    fun aDamagedMemoryIsDeletedRatherThanKept() {
        store.text = "not ours"
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
    fun theFileStoreKeepsTheTwoPartsApart() = withDir { dir ->
        val file = File(File(dir, "reader"), "last.txt")
        val store = FileMemoryStore(file) { it.run() }
        assertNull(store.read().text)
        store.write("text one", "1 0 0")
        store.write(null, "1 4 2")
        assertEquals("text one", store.read().text)
        assertEquals("1 4 2", store.read().place)
        assertFalse(File(file.parentFile, "last.txt.tmp").exists())
        assertFalse(File(file.parentFile, FileMemoryStore.PLACE_NAME + ".tmp").exists())
        store.delete()
        assertNull(store.read().text)
        assertNull(store.read().place)
        assertFalse(file.exists())
    }

    @Test
    fun aBurstOfChangesCostsOneWriteAndKeepsAWaitingText() = withDir { dir ->
        val file = File(dir, "last.txt")
        val queued = ArrayList<Runnable>()
        val store = FileMemoryStore(file) { queued += it }
        store.write("text", "1 0 0")
        store.write(null, "1 1 0")
        store.write(null, "1 2 0")
        assertEquals(1, queued.size)
        queued.removeAt(0).run()
        assertEquals("the text waiting wasn't lost to the place-only writes", "text", file.readText())
        assertEquals("1 2 0", store.read().place)
        // A delete after writes, before they ran: everything ends up gone.
        store.write(null, "1 3 0")
        store.delete()
        store.write(null, "1 4 0") // a place without its text means nothing
        assertEquals(1, queued.size)
        queued.removeAt(0).run()
        assertFalse(file.exists())
        assertNull(store.read().place)
        // A new text after a delete is written.
        store.delete()
        store.write("new", "2 0 0")
        queued.removeAt(0).run()
        assertEquals("new", store.read().text)
        // A change while a run is under way schedules another run.
        store.write(null, "2 1 0")
        queued.removeAt(0).run()
        store.write(null, "2 2 0")
        assertEquals(1, queued.size)
        queued.removeAt(0).run()
        assertEquals("2 2 0", store.read().place)
    }

    @Test
    fun aFailedWriteKeepsThePreviousVersionAndThrowsNothing() = withDir { dir ->
        val file = File(dir, "last.txt")
        val store = FileMemoryStore(file) { it.run() }
        store.write("kept", "1 1 0")
        // The temporary file's place is taken by a folder: the text write fails...
        File(File(dir, "last.txt.tmp"), "blocker").mkdirs()
        store.write("lost", "2 0 0")
        assertEquals("kept", store.read().text)
        // ...and the place that belonged to the new text isn't written either.
        assertEquals("1 1 0", store.read().place)
    }

    @Test
    fun aFileTooBigToBeOursIsNotRead() = withDir { dir ->
        val file = File(dir, "last.txt")
        file.writeBytes(ByteArray((FileMemoryStore.MAX_BYTES + 1).toInt()))
        File(dir, FileMemoryStore.PLACE_NAME).writeText("x".repeat(1000))
        val parts = FileMemoryStore(file) { it.run() }.read()
        assertNull(parts.text)
        assertNull(parts.place)
    }
}
