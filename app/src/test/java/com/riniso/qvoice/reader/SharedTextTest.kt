package com.riniso.qvoice.reader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/** Text files shared to the reader: decoding, the size cap, telling text from binary. */
class SharedTextTest {

    private val tamil = "வணக்கம், எப்படி இருக்கிறீர்கள்?"

    @Test
    fun utf8WithOrWithoutItsMark() {
        assertEquals(tamil, SharedText.decode(tamil.toByteArray(Charsets.UTF_8)))
        val marked = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Hello".toByteArray(Charsets.UTF_8)
        assertEquals("Hello", SharedText.decode(marked))
    }

    @Test
    fun utf16AsWindowsNotepadSavesIt() {
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + tamil.toByteArray(Charsets.UTF_16LE)
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + tamil.toByteArray(Charsets.UTF_16BE)
        assertEquals(tamil, SharedText.decode(le))
        assertEquals(tamil, SharedText.decode(be))
    }

    @Test
    fun badBytesBecomeReplacementCharactersNotErrors() {
        val text = SharedText.decode(byteArrayOf('O'.code.toByte(), 0xC3.toByte(), 'K'.code.toByte()))
        assertEquals("O�K", text)
        assertTrue("a stray bad byte in real text is still text", SharedText.looksLikeText("A long enough sentence with one bad byte � in it, read anyway."))
    }

    @Test
    fun binaryFilesSentAsTextAreRefused() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13) +
            ByteArray(500) { (it * 37 + 128).toByte() }
        assertFalse(SharedText.looksLikeText(SharedText.decode(png)))
        assertFalse(SharedText.looksLikeText("   "))
        assertTrue(SharedText.looksLikeText(tamil))
    }

    @Test
    fun readingStopsAtTheCapEvenFromASlowStream() {
        val data = ByteArray(5000) { it.toByte() }
        // A stream that hands out at most 7 bytes per read, like a slow provider.
        val trickle = object : InputStream() {
            private val inner = ByteArrayInputStream(data)
            override fun read(): Int = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, minOf(len, 7))
        }
        assertArrayEquals(data.copyOf(1000), SharedText.readUpTo(trickle, max = 1000))
        assertArrayEquals(data, SharedText.readUpTo(ByteArrayInputStream(data), max = 10_000))
    }
}
