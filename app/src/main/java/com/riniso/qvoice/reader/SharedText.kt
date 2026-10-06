package com.riniso.qvoice.reader

import java.io.InputStream

/**
 * Text files shared to the reader (a .txt from Files, a mail attachment, a
 * notes app's export): read with a size cap and decoded the way text
 * editors do. Plain Kotlin, so it is unit-tested; ReaderActivity does the
 * reading from the sharing app's content provider.
 */
object SharedText {

    /**
     * Read at most this much: more than ReaderText.MAX_CHARS takes in any
     * encoding (100,000 characters are at most 400 KB of UTF-8), so a longer
     * file is still recognised as cut (and says so), and a huge one never
     * fills the memory.
     */
    const val MAX_BYTES = 1 shl 20

    fun readUpTo(input: InputStream, max: Int = MAX_BYTES): ByteArray {
        val buffer = ByteArray(max)
        var filled = 0
        while (filled < max) {
            val n = input.read(buffer, filled, max - filled)
            if (n < 0) break
            filled += n
        }
        return buffer.copyOf(filled)
    }

    /**
     * UTF-8, unless a byte-order mark says UTF-16 (what Windows Notepad saves
     * as "Unicode") or marks UTF-8 explicitly. Undecodable bytes become
     * U+FFFD rather than an error (see [looksLikeText]).
     */
    fun decode(bytes: ByteArray): String = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        bytes.startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> String(bytes, Charsets.UTF_8)
    }

    /**
     * False for what isn't text even though it was sent as text (a PDF or an
     * image with the wrong type): more than 2% of its first 4,096 characters
     * undecodable or NUL. Reading that aloud would be noise.
     */
    fun looksLikeText(text: String): Boolean {
        val head = text.take(4096)
        if (head.isBlank()) return false
        val bad = head.count { it == '�' || it == '\u0000' }
        return bad * 50 <= head.length
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
}
