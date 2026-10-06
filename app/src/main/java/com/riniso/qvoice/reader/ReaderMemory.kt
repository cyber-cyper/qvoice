package com.riniso.qvoice.reader

import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/**
 * Remembers the reader's text and place on the phone, so reading continues
 * after QVoice was closed, killed in the background or the phone restarted
 * (D-050). Only the latest text is kept: a new text replaces it, and an
 * empty reader ("Read something else") deletes it.
 *
 * What is saved is the prepared paragraphs, not the raw text: the place is a
 * paragraph number, and preparing the same text again could split it
 * differently (TalkBack on or off changes the paragraph length, see
 * ReaderText), which would move the place.
 *
 * Plain Kotlin and confined to the main thread like ReadAloud; the file work
 * is the [Store]'s.
 */
class ReaderMemory(private val store: Store) {

    /** Where the memory lives (FileMemoryStore in the app). */
    interface Store {
        /** The saved text, or null if there is none or it can't be read. */
        fun read(): String?

        /** Replaces the saved text; may finish later, but in order with [delete]. */
        fun write(text: String)

        fun delete()
    }

    data class Saved(val paragraphs: List<String>, val index: Int, val truncated: Boolean)

    // What the store holds now, as far as this object knows: the paragraph
    // list instance (ReadAloud keeps one per text) and the place.
    private var savedParagraphs: List<String>? = null
    private var savedPlace = -1

    /** The remembered text, if any; a damaged file is deleted rather than kept. */
    fun restore(): Saved? {
        val text = store.read() ?: return null
        val saved = decode(text)
        if (saved == null) {
            store.delete()
            return null
        }
        savedParagraphs = saved.paragraphs
        savedPlace = saved.index
        return saved
    }

    /**
     * Follows the reader (every state it publishes): saves a new text or a
     * new place, deletes the memory when the reader is emptied, and does
     * nothing for the many changes that touch neither (play, pause, speed,
     * problems, the sleep timer).
     */
    fun onState(state: ReadAloud.State) {
        if (state.paragraphs.isEmpty()) {
            if (savedParagraphs != null) {
                savedParagraphs = null
                savedPlace = -1
                store.delete()
            }
            return
        }
        // A finished text starts over next time, as Play does (ReadAloud.play).
        val place = if (state.status == ReadAloud.Status.FINISHED) 0 else state.index
        if (state.paragraphs === savedParagraphs && place == savedPlace) return
        savedParagraphs = state.paragraphs
        savedPlace = place
        store.write(encode(Saved(state.paragraphs, place, state.truncated)))
    }

    companion object {
        /** First line of the file; a different one (another version, damage) is ignored. */
        const val HEADER = "QVoice reader 1"

        /**
         * A header line, the place, the "cut" flag, then one paragraph per
         * line. Prepared paragraphs never hold a line break (ReaderText turns
         * every run of whitespace into one space); one that did would be
         * turned into a space here rather than split in two.
         */
        fun encode(saved: Saved): String = buildString {
            append(HEADER).append('\n')
            append("index=").append(saved.index).append('\n')
            append("truncated=").append(if (saved.truncated) 1 else 0).append('\n')
            for (paragraph in saved.paragraphs) {
                append(paragraph.replace('\r', ' ').replace('\n', ' ')).append('\n')
            }
        }

        /** The saved text, or null if [text] isn't one this version wrote. */
        fun decode(text: String): Saved? {
            val lines = text.split('\n')
            if (lines.size < 4 || lines[0] != HEADER) return null
            val index = lines[1].takeIf { it.startsWith("index=") }?.substring("index=".length)?.toIntOrNull() ?: return null
            val truncated = when (lines[2]) {
                "truncated=1" -> true
                "truncated=0" -> false
                else -> return null
            }
            // The file ends with a line break, so the last piece is empty.
            val paragraphs = lines.subList(3, lines.size).filter { it.isNotEmpty() }
            if (paragraphs.isEmpty()) return null
            return Saved(paragraphs, index.coerceIn(0, paragraphs.lastIndex), truncated)
        }
    }
}

/**
 * [ReaderMemory.Store] in a file in QVoice's no-backup folder
 * (Context.noBackupFilesDir: private to QVoice, never in a backup or a
 * device-to-device transfer, deleted with the app's storage).
 *
 * Writes run on [executor], in order, and only the latest one pending is
 * done: a burst of place changes (Next, Next, Next) costs one write. Each
 * write goes to a temporary file first and is then renamed over the old
 * one, so a crash or a full disk mid-write leaves the previous text intact,
 * never half a file. Reading is on the caller's thread: once, when the
 * reader is first needed.
 */
class FileMemoryStore(private val file: File, private val executor: Executor) : ReaderMemory.Store {

    private sealed interface Op {
        class Write(val text: String) : Op
        object Delete : Op
    }

    private val pending = AtomicReference<Op?>(null)
    private val temp = File(file.parentFile, file.name + ".tmp")

    override fun read(): String? = try {
        // Far bigger than any text the reader holds (100,000 characters, at
        // most 4 bytes each in UTF-8): anything larger isn't ours to read.
        if (!file.isFile || file.length() > MAX_BYTES) null else file.readText(Charsets.UTF_8)
    } catch (e: Exception) {
        // IOException, SecurityException: no memory is better than no reader.
        null
    }

    override fun write(text: String) = submit(Op.Write(text))

    override fun delete() = submit(Op.Delete)

    private fun submit(op: Op) {
        // A run is already scheduled if something was pending: it will take
        // this newer operation instead.
        if (pending.getAndSet(op) == null) executor.execute(::flush)
    }

    private fun flush() {
        val op = pending.getAndSet(null) ?: return
        // Nothing may escape: an exception on this thread would end the whole
        // process, engine included. Memory is a comfort, not a must.
        try {
            when (op) {
                is Op.Write -> {
                    file.parentFile?.mkdirs()
                    temp.writeText(op.text, Charsets.UTF_8)
                    // Not expected to fail on Android's file systems; if it
                    // does, the previous copy stays.
                    if (!temp.renameTo(file)) temp.delete()
                }
                Op.Delete -> {
                    file.delete()
                    temp.delete()
                }
            }
        } catch (e: Exception) {
            // Disk full, say: the previous text stays as it was.
            temp.delete()
        }
    }

    companion object {
        const val MAX_BYTES = 1L shl 20
    }
}
