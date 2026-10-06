package com.riniso.qvoice.reader

import java.io.File
import java.util.concurrent.Executor

/**
 * Remembers the reader's text and place on the phone, so reading continues
 * after QVoice was closed, killed in the background or the phone restarted
 * (D-050). Only the latest text is kept: a new text replaces it, and an
 * empty reader ("Read something else") deletes it.
 *
 * What is saved is the prepared paragraphs, not the raw text: the place is a
 * paragraph and sentence number, and preparing the same text again could
 * split it differently (TalkBack on or off changes the paragraph length, see
 * ReaderText), which would move the place.
 *
 * Two parts, because they change at very different rates: the text (written
 * once per text, up to a few hundred kilobytes) and the place (a few bytes,
 * written at every sentence). Each text gets a new id, written into both, so
 * a place can never be applied to another text: if the two don't match (the
 * phone died between the writes), reading resumes at the start.
 *
 * Plain Kotlin and confined to the main thread like ReadAloud; the file work
 * is the [Store]'s.
 */
class ReaderMemory(
    private val store: Store,
    /** A new id for each text; only has to differ from the previous one. */
    private val newId: () -> Long = { System.nanoTime() },
) {

    /** Where the memory lives (FileMemoryStore in the app). */
    interface Store {
        /** What is saved: either part null if missing or unreadable. */
        fun read(): Parts

        /**
         * Saves [place], and [text] as well unless it is null (unchanged).
         * May finish later, but in order with [delete].
         */
        fun write(text: String?, place: String)

        fun delete()
    }

    class Parts(val text: String?, val place: String?)

    /** The text, and the place: sentence [sentence] of paragraph [index]. */
    data class Saved(val paragraphs: List<String>, val index: Int, val sentence: Int, val truncated: Boolean)

    // What the store holds now, as far as this object knows: the paragraph
    // list instance (ReadAloud keeps one per text), its id and the place.
    private var savedParagraphs: List<String>? = null
    private var savedId = 0L
    private var savedPlace: Pair<Int, Int>? = null

    /** The remembered text, if any; a damaged one is deleted rather than kept. */
    fun restore(): Saved? {
        val parts = store.read()
        val text = decodeText(parts.text ?: return null)
        if (text == null) {
            store.delete()
            return null
        }
        val place = text.legacyPlace ?: decodePlace(parts.place, text.id) ?: (0 to 0)
        val saved = Saved(text.paragraphs, place.first.coerceIn(0, text.paragraphs.lastIndex), place.second.coerceAtLeast(0), text.truncated)
        savedParagraphs = saved.paragraphs
        savedPlace = saved.index to saved.sentence
        if (text.legacyPlace != null) {
            // Written by slices 18 to 23 (one file, the place inside): rewrite
            // it as two parts now, so later place changes take effect.
            savedId = newId()
            store.write(encodeText(savedId, saved.paragraphs, saved.truncated), encodePlace(savedId, savedPlace!!))
        } else {
            savedId = text.id
        }
        return saved
    }

    /**
     * Follows the reader (every state it publishes): saves a new text with
     * its place, or only a new place, deletes the memory when the reader is
     * emptied, and does nothing for the many changes that touch neither
     * (play, pause, speed, problems, the sleep timer).
     */
    fun onState(state: ReadAloud.State) {
        if (state.paragraphs.isEmpty()) {
            if (savedParagraphs != null) {
                savedParagraphs = null
                savedPlace = null
                store.delete()
            }
            return
        }
        // A finished text starts over next time, as Play does (ReadAloud.play).
        val place = if (state.status == ReadAloud.Status.FINISHED) 0 to 0 else state.index to state.sentence
        if (state.paragraphs === savedParagraphs) {
            if (place == savedPlace) return
            savedPlace = place
            store.write(null, encodePlace(savedId, place))
            return
        }
        savedParagraphs = state.paragraphs
        savedId = newId()
        savedPlace = place
        store.write(encodeText(savedId, state.paragraphs, state.truncated), encodePlace(savedId, place))
    }

    /** A decoded text part; [legacyPlace] only for the single-file version 1. */
    class Text(val id: Long, val paragraphs: List<String>, val truncated: Boolean, val legacyPlace: Pair<Int, Int>?)

    companion object {
        /**
         * First line of the text part; any other (damage, a future version)
         * is ignored. Version 2 (slice 24) keeps the place apart; version 1
         * (slices 18 to 23: the paragraph inside, no sentence) is still read.
         */
        const val HEADER = "QVoice reader 2"

        internal const val HEADER_V1 = "QVoice reader 1"

        /**
         * A header line, the text's id, the "cut" flag, then one paragraph per
         * line. Prepared paragraphs never hold a line break (ReaderText turns
         * every run of whitespace into one space); one that did would be
         * turned into a space here rather than split in two.
         */
        fun encodeText(id: Long, paragraphs: List<String>, truncated: Boolean): String = buildString {
            append(HEADER).append('\n')
            append("id=").append(id).append('\n')
            append("truncated=").append(if (truncated) 1 else 0).append('\n')
            for (paragraph in paragraphs) {
                append(paragraph.replace('\r', ' ').replace('\n', ' ')).append('\n')
            }
        }

        /** "<text id> <paragraph> <sentence>". */
        fun encodePlace(id: Long, place: Pair<Int, Int>): String = "$id ${place.first} ${place.second}"

        /** The text part, or null if it isn't one this or the previous version wrote. */
        fun decodeText(text: String): Text? {
            val lines = text.split('\n')
            val v1 = when (lines.firstOrNull()) {
                HEADER -> false
                HEADER_V1 -> true
                else -> return null
            }
            if (lines.size < 4) return null
            val key = if (v1) "index=" else "id="
            val number = lines[1].takeIf { it.startsWith(key) }?.substring(key.length)?.toLongOrNull() ?: return null
            val truncated = when (lines[2]) {
                "truncated=1" -> true
                "truncated=0" -> false
                else -> return null
            }
            // The file ends with a line break, so the last piece is empty.
            val paragraphs = lines.subList(3, lines.size).filter { it.isNotEmpty() }
            if (paragraphs.isEmpty()) return null
            return if (v1) {
                Text(0L, paragraphs, truncated, legacyPlace = number.toInt() to 0)
            } else {
                Text(number, paragraphs, truncated, legacyPlace = null)
            }
        }

        /** The place, if [place] is one and belongs to the text with [id]. */
        fun decodePlace(place: String?, id: Long): Pair<Int, Int>? {
            val parts = place?.trim()?.split(' ') ?: return null
            if (parts.size != 3 || parts[0].toLongOrNull() != id) return null
            val paragraph = parts[1].toIntOrNull() ?: return null
            val sentence = parts[2].toIntOrNull() ?: return null
            return paragraph to sentence
        }
    }
}

/**
 * [ReaderMemory.Store] in two files in QVoice's no-backup folder
 * (Context.noBackupFilesDir: private to QVoice, never in a backup or a
 * device-to-device transfer, deleted with the app's storage): [textFile]
 * and, next to it, the place.
 *
 * Writes run on [executor], in order, and only the latest one pending is
 * done (a burst of place changes, Next, Next, Next, costs one write; a text
 * still waiting is kept when only the place changes after it). Each file is
 * written to a temporary file first and then renamed over the old one, so a
 * crash or a full disk mid-write leaves the previous version intact, never
 * half a file. Reading is on the caller's thread: once, when the reader is
 * first needed.
 */
class FileMemoryStore(private val textFile: File, private val executor: Executor) : ReaderMemory.Store {

    private sealed interface Op {
        class Write(val text: String?, val place: String) : Op
        object Delete : Op
    }

    private val placeFile = File(textFile.parentFile, PLACE_NAME)
    private val lock = Any()
    private var pending: Op? = null
    private var scheduled = false

    override fun read(): ReaderMemory.Parts =
        ReaderMemory.Parts(readUpTo(textFile, MAX_BYTES), readUpTo(placeFile, MAX_PLACE_BYTES))

    override fun write(text: String?, place: String) = submit(Op.Write(text, place))

    override fun delete() = submit(Op.Delete)

    private fun submit(op: Op) {
        synchronized(lock) {
            pending = merge(pending, op)
            // A run already scheduled takes the merged operation.
            if (scheduled) return
            scheduled = true
        }
        executor.execute(::flush)
    }

    /**
     * What one write has to do for [earlier] then [later]: the newest place;
     * the newest text, or the earlier one if the later write left it alone;
     * and after a delete, a write only if it brings a text (a place without
     * its text means nothing).
     */
    private fun merge(earlier: Op?, later: Op): Op {
        if (later !is Op.Write) return later // a delete supersedes everything before it
        return when (earlier) {
            is Op.Write -> Op.Write(later.text ?: earlier.text, later.place)
            Op.Delete -> if (later.text == null) earlier else later
            null -> later
        }
    }

    private fun flush() {
        val op = synchronized(lock) {
            scheduled = false
            pending.also { pending = null }
        } ?: return
        // Nothing may escape: an exception on this thread would end the whole
        // process, engine included. Memory is a comfort, not a must.
        try {
            when (op) {
                is Op.Write -> {
                    op.text?.let { replace(textFile, it) }
                    replace(placeFile, op.place)
                }
                Op.Delete -> {
                    for (file in listOf(textFile, placeFile)) {
                        file.delete()
                        temp(file).delete()
                    }
                }
            }
        } catch (e: Exception) {
            // Disk full, say: what was there stays as it was.
        }
    }

    /** [file] replaced by [content] in one step (temporary file, then rename). */
    private fun replace(file: File, content: String) {
        val temp = temp(file)
        try {
            file.parentFile?.mkdirs()
            temp.writeText(content, Charsets.UTF_8)
            // Not expected to fail on Android's file systems; if it does, the
            // previous copy stays.
            if (!temp.renameTo(file)) temp.delete()
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun temp(file: File) = File(file.parentFile, file.name + ".tmp")

    private fun readUpTo(file: File, max: Long): String? = try {
        // Far bigger than anything QVoice writes there: a larger file isn't ours to read.
        if (!file.isFile || file.length() > max) null else file.readText(Charsets.UTF_8)
    } catch (e: Exception) {
        // IOException, SecurityException: no memory is better than no reader.
        null
    }

    companion object {
        /** The text part: 100,000 characters at most 4 bytes each, with room to spare. */
        const val MAX_BYTES = 1L shl 20

        private const val MAX_PLACE_BYTES = 256L

        /** The place part, next to the text file. */
        const val PLACE_NAME = "place.txt"
    }
}
