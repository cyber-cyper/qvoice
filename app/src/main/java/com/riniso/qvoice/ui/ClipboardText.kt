package com.riniso.qvoice.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.text.Html

/**
 * The clipboard's text, for the reader's Paste button and the "Read copied
 * text" shortcut — the only two places QVoice reads the clipboard, both at
 * the user's request. Android 10+ lets only the app in focus read it (the
 * reader therefore waits for its window's focus), and Android 12+ shows
 * "QVoice pasted from your clipboard" each time the content is read.
 */
object ClipboardText {

    sealed interface Result {
        class Text(val text: CharSequence) : Result

        /**
         * The app that copied it marked it sensitive (a password manager, a
         * one-time code): not read unless the user asks again.
         */
        object Sensitive : Result

        /** Nothing, or nothing that is text (an image, a file). */
        object Empty : Result
    }

    /**
     * @param allowSensitive true when the user has explicitly asked for this
     *   very clip (Paste, "Read it anyway").
     */
    fun read(context: Context, allowSensitive: Boolean): Result {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return Result.Empty
        // The description first: reading it doesn't count as reading the
        // clip (no "pasted" notice), so a sensitive clip is never even read.
        val description = clipboard.primaryClipDescription ?: return Result.Empty
        return decide(isSensitive(description), allowSensitive) {
            val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            // Text, or the text of copied HTML. Never coerceToText(): for a
            // copied file it would open the file's content on the main thread.
            item?.text ?: item?.htmlText?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT) }
        }
    }

    /**
     * [read]'s rule, apart for the unit tests: a clip marked sensitive is
     * refused before its [content] is fetched at all, unless the user asked
     * for it; nothing but blank space counts as nothing.
     */
    fun decide(sensitive: Boolean, allowSensitive: Boolean, content: () -> CharSequence?): Result {
        if (sensitive && !allowSensitive) return Result.Sensitive
        val text = content()
        return if (text.isNullOrBlank()) Result.Empty else Result.Text(text)
    }

    /**
     * ClipDescription.EXTRA_IS_SENSITIVE (API 33) by its value, which password
     * managers and keyboards set on older versions too.
     */
    private fun isSensitive(description: ClipDescription): Boolean =
        description.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true

    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
}
