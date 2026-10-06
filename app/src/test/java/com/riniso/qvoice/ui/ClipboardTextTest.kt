package com.riniso.qvoice.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** ClipboardText's rule: a sensitive clip isn't even fetched unless asked for; blank is nothing. */
class ClipboardTextTest {

    @Test
    fun aSensitiveClipIsRefusedWithoutBeingRead() {
        var fetched = false
        val result = ClipboardText.decide(sensitive = true, allowSensitive = false) {
            fetched = true
            "hunter2"
        }
        assertSame(ClipboardText.Result.Sensitive, result)
        assertFalse(fetched)
    }

    @Test
    fun askingAgainReadsASensitiveClip() {
        val result = ClipboardText.decide(sensitive = true, allowSensitive = true) { "123 456" }
        assertEquals("123 456", (result as ClipboardText.Result.Text).text)
    }

    @Test
    fun ordinaryTextIsReadAndBlankIsNothing() {
        assertEquals("Hello.", (ClipboardText.decide(false, false) { "Hello." } as ClipboardText.Result.Text).text)
        assertSame(ClipboardText.Result.Empty, ClipboardText.decide(false, false) { null })
        assertSame(ClipboardText.Result.Empty, ClipboardText.decide(false, false) { " \n\t " })
        assertTrue(ClipboardText.decide(false, true) { "x" } is ClipboardText.Result.Text)
    }
}
