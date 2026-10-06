package com.riniso.qvoice.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** Speaks utterances; QVoice's own engine through TextToSpeech in the app (TtsSpeaker). */
interface Speaker {

    /** Called on the main thread. */
    interface Listener {
        fun onStart(utteranceId: String)
        fun onDone(utteranceId: String)
        fun onError(utteranceId: String, errorCode: Int)
    }

    var listener: Listener?

    /**
     * Queues [text]; [flush] drops everything queued or playing first.
     * [ahead]: queued while the previous text plays, so its first sound
     * isn't waited for (the engine then skips its fast-start cut).
     * @return false if the request was refused outright (errors after that
     *   arrive as [Listener.onError]).
     */
    fun speak(text: String, utteranceId: String, flush: Boolean, rate: Float, locale: Locale, ahead: Boolean = false): Boolean

    fun stop()

    /** Lets go of the engine; a later [speak] reconnects. */
    fun shutdown()

    /**
     * A language's default voice changed: from the next request on, pick the
     * voice for each language afresh instead of reusing the one picked before.
     */
    fun voicesChanged()
}

/** Audio focus, and "headphones unplugged", while reading (SystemPlaybackGuard). */
interface PlaybackGuard {

    /** Called on the main thread. */
    interface Listener {
        /** Another app took the audio, or the headphones came out. [resumeLater]: a call or alert that will end. */
        fun onPauseRequested(resumeLater: Boolean)

        /** The audio is back after a temporary loss. */
        fun onResumeAllowed()
    }

    /** @return false if the audio can't be had now (e.g. during a phone call). */
    fun start(listener: Listener): Boolean

    fun stop()
}

/**
 * Reads a text aloud sentence by sentence and holds what the reader screen
 * and the reading notification show. App-wide (AppGraph), so reading carries
 * on while the screen is recreated, closed or off (ReadAloudService keeps
 * the app running meanwhile). Confined to the main thread: the speaker, the
 * guard, the notification's buttons and the media session all deliver their
 * calls there, so there is no locking and no callback can race a tap.
 *
 * A listening session starts with Play and lasts through pauses until
 * [stop] or new text: PLAYING, PAUSED and FINISHED are in a session, READY
 * (text loaded, not started, or session ended) and EMPTY are not. The sleep
 * timer ([setSleepTimer]) pauses reading between paragraphs once its time is
 * up; it needs no timer thread of its own, the sentence ends are its ticks.
 *
 * Why sentences as separate utterances, one queued ahead (D-055): the reader
 * then knows which sentence is playing, to highlight it within its paragraph,
 * to skip back or ahead by one (a missed sentence is the usual reason to go
 * back), and to resume exactly there; while the engine starts the next one as
 * the current one plays out, so no gap is added. The text stays in paragraphs
 * for showing it, for the place in the notification ("Paragraph 3 of 12")
 * and for the language, which is picked per paragraph as before. Every
 * utterance id carries a generation number that a stop, jump or reload
 * increases, so late callbacks of dropped utterances are ignored and never
 * move the highlight backwards.
 */
class ReadAloud(
    private val speaker: Speaker,
    private val guard: PlaybackGuard,
    /** The locale to read a paragraph in, or null if no installed voice can (ReaderLanguage). */
    private val localeFor: (String) -> Locale?,
    initialRate: Float = 1f,
    /** Remembers the reader's speed between sessions. */
    private val onRateChanged: (Float) -> Unit = {},
    /**
     * TalkBack (or another screen reader) is on. Android's TTS service
     * speaks one utterance at a time for all apps, so while the reader holds
     * it, TalkBack waits. Then the reader queues nothing ahead, so TalkBack
     * gets a turn after every sentence.
     */
    private val screenReaderOn: () -> Boolean = { false },
    /** Milliseconds on a clock that never jumps (elapsedRealtime in the app): the sleep timer's. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : Speaker.Listener, PlaybackGuard.Listener {

    enum class Status { EMPTY, READY, PLAYING, PAUSED, FINISHED }

    enum class Problem {
        /** No installed voice reads the current paragraph's script. */
        NO_VOICE,

        /** The engine refused or failed the request. */
        ENGINE,

        /** The audio is taken (a phone call, for example). */
        AUDIO_BUSY,
    }

    data class State(
        val paragraphs: List<String> = emptyList(),
        /** The paragraph being read, or where reading resumes. */
        val index: Int = 0,
        /**
         * The sentence of paragraph [index] being read, or where reading
         * resumes, numbered as ReaderText.sentenceRanges numbers them.
         */
        val sentence: Int = 0,
        /** The sentence at the place is the text's last: there is no "next". */
        val lastSentence: Boolean = false,
        val status: Status = Status.EMPTY,
        val rate: Float = 1f,
        /** The text was longer than [ReaderText.MAX_CHARS] and was cut. */
        val truncated: Boolean = false,
        val problem: Problem? = null,
        /** When the sleep timer stops reading, on [clock]'s time line; null: no timer. */
        val sleepAt: Long? = null,
        /**
         * Reading, but the voice hasn't started the sentence yet: after Play
         * or a jump, and between sentences while the next one is computed.
         * The reader shows it (after a moment, so a gap of a few milliseconds
         * doesn't flicker): core app quality asks for audio within a second
         * of Play or a visible sign that it's coming.
         */
        val preparing: Boolean = false,
    )

    /** One utterance: sentence [sentence] of paragraph [paragraph], characters [range] of it. */
    private class Piece(val paragraph: Int, val sentence: Int, val range: IntRange, val endsParagraph: Boolean)

    private val _state = MutableStateFlow(State(rate = initialRate))
    val state: StateFlow<State> = _state.asStateFlow()

    /** The text's sentences in reading order; built with the text, never changed after. */
    private var pieces: List<Piece> = emptyList()

    /** For each paragraph, the number of its first piece. */
    private var firstPiece = IntArray(0)

    private var generation = 0
    private var queuedUpTo = -1
    private var resumeOnFocusGain = false

    init {
        speaker.listener = this
    }

    /** Replaces the text (from a share, a selection or the paste field); [play] starts reading it. */
    fun load(text: CharSequence, play: Boolean) {
        halt()
        val prepared = ReaderText.prepare(
            text,
            maxParagraph = if (screenReaderOn()) ReaderText.SCREEN_READER_PARAGRAPH else ReaderText.MAX_PARAGRAPH,
        )
        divide(prepared.paragraphs)
        _state.update {
            State(
                paragraphs = prepared.paragraphs,
                status = if (prepared.paragraphs.isEmpty()) Status.EMPTY else Status.READY,
                rate = it.rate,
                truncated = prepared.truncated,
            ).placedAt(0)
        }
        if (play) play()
    }

    /**
     * Puts a remembered text back (ReaderMemory) without reading it: ready at
     * sentence [sentence] of paragraph [index], as if loaded and moved there.
     * Only into an empty reader, so it never replaces a text that arrived
     * first.
     */
    fun restore(paragraphs: List<String>, index: Int, sentence: Int, truncated: Boolean) {
        if (_state.value.status != Status.EMPTY || paragraphs.isEmpty()) return
        divide(paragraphs)
        val paragraph = index.coerceIn(0, paragraphs.lastIndex)
        _state.update {
            State(paragraphs = paragraphs, status = Status.READY, rate = it.rate, truncated = truncated)
                .placedAt(firstPiece[paragraph] + sentence.coerceIn(0, sentenceCount(paragraph) - 1))
        }
    }

    fun play() {
        val s = _state.value
        if (s.paragraphs.isEmpty() || s.status == Status.PLAYING) return
        // A timer that ran out while paused doesn't stop the new start.
        if (sleepDue()) _state.update { it.copy(sleepAt = null) }
        if (!guard.start(this)) {
            _state.update { it.copy(problem = Problem.AUDIO_BUSY) }
            return
        }
        startAt(if (s.status == Status.FINISHED) 0 else place())
    }

    fun pause() {
        if (_state.value.status != Status.PLAYING) return
        halt()
        _state.update { it.copy(status = Status.PAUSED, preparing = false) }
    }

    fun toggle() = if (_state.value.status == Status.PLAYING) pause() else play()

    /** The next sentence. */
    fun next() = jumpToPiece(place() + 1)

    /** The previous sentence (or the start of the first one). */
    fun previous() = jumpToPiece(maxOf(0, place() - 1))

    /** Moves to the start of paragraph [index] (a tap on it); keeps reading if it was reading. */
    fun jumpTo(index: Int) {
        if (index !in _state.value.paragraphs.indices) return
        jumpToPiece(firstPiece[index])
    }

    /**
     * The sleep timer: reading stops [minutes] from now, at the end of the
     * paragraph being read then (never mid-sentence); null turns it off. The
     * time runs whether reading or paused, like an alarm: "stops at 23:45".
     */
    fun setSleepTimer(minutes: Int?) {
        _state.update { it.copy(sleepAt = minutes?.let { m -> clock() + m * 60_000L }) }
    }

    /** From the current sentence's start when reading, so the change is heard at once. */
    fun setRate(rate: Float) {
        _state.update { it.copy(rate = rate) }
        onRateChanged(rate)
        restartSentence()
    }

    /** The language paragraph [index] is read in (the voice choice lists its voices); null if no voice can. */
    fun localeOf(index: Int): Locale? = _state.value.paragraphs.getOrNull(index)?.let(localeFor)

    /**
     * The user picked another voice for a language (the reader's voice
     * choice made it that language's default): voices are picked afresh, and
     * the sentence being read starts over so the new voice is heard at once.
     */
    fun voiceChanged() {
        speaker.voicesChanged()
        restartSentence()
    }

    /**
     * Ends the listening session: stops reading, gives the audio back and
     * lets go of the engine, but keeps the text and the place (a finished
     * text starts over), so the reader screen looks the same and Play goes
     * on from there. The notification goes with the session (NowPlaying).
     * For the reader closing while not reading, the notification's Stop,
     * and a long pause (ReadAloudService).
     */
    fun stop() {
        halt()
        speaker.shutdown()
        _state.update {
            when (it.status) {
                Status.EMPTY, Status.READY -> it
                Status.FINISHED -> it.placedAt(0).copy(status = Status.READY)
                Status.PLAYING, Status.PAUSED -> it.copy(status = Status.READY)
            }.copy(sleepAt = null, preparing = false)
        }
    }

    // ---- Speaker.Listener ----

    override fun onStart(utteranceId: String) {
        val piece = current(utteranceId) ?: return
        _state.update { it.placedAt(piece).copy(preparing = false) }
        // One ahead, unless a screen reader needs its turns (see screenReaderOn)
        // or the sleep timer is about to stop reading at this paragraph's end
        // (see sleepsSoon); if it can't be queued, onDone says why.
        if (screenReaderOn()) return
        if (sleepsSoon() && pieces[piece].endsParagraph) return
        enqueue(piece + 1, flush = false, ahead = true)
    }

    override fun onDone(utteranceId: String) {
        val piece = current(utteranceId) ?: return
        if (piece == pieces.lastIndex) {
            halt()
            _state.update { it.placedAt(piece).copy(status = Status.FINISHED, sleepAt = null, preparing = false) }
            return
        }
        if (pieces[piece].endsParagraph && sleepDue()) {
            // The sleep timer ran out during this paragraph: stop here, between
            // paragraphs, where Play will go on.
            halt()
            _state.update { it.placedAt(piece + 1).copy(status = Status.PAUSED, sleepAt = null, preparing = false) }
            return
        }
        // From here the next sentence is on its way: it starts the moment its
        // audio is ready (onStart), at once when it was computed ahead.
        if (queuedUpTo <= piece) {
            val problem = enqueue(piece + 1, flush = false)
            if (problem != null) {
                // It can't be spoken: stop there and say why.
                halt()
                _state.update { it.placedAt(piece + 1).copy(status = Status.PAUSED, problem = problem, preparing = false) }
                return
            }
        }
        _state.update { it.copy(preparing = true) }
    }

    override fun onError(utteranceId: String, errorCode: Int) {
        current(utteranceId) ?: return
        halt()
        _state.update { it.copy(status = Status.PAUSED, problem = Problem.ENGINE, preparing = false) }
    }

    // ---- PlaybackGuard.Listener ----

    override fun onPauseRequested(resumeLater: Boolean) {
        if (_state.value.status != Status.PLAYING) return
        if (!resumeLater) {
            pause()
            return
        }
        // A call or an alert: silent, but still holding the audio request,
        // which is what brings the "audio is back" callback.
        silence()
        _state.update { it.copy(status = Status.PAUSED, preparing = false) }
        resumeOnFocusGain = true
    }

    override fun onResumeAllowed() {
        if (!resumeOnFocusGain) return
        resumeOnFocusGain = false
        if (_state.value.status == Status.PAUSED) startAt(place())
    }

    // ---- internals ----

    /** Splits [paragraphs] into the pieces read one at a time. */
    private fun divide(paragraphs: List<String>) {
        val list = ArrayList<Piece>()
        val first = IntArray(paragraphs.size)
        paragraphs.forEachIndexed { p, text ->
            first[p] = list.size
            val ranges = ReaderText.sentenceRanges(text)
            ranges.forEachIndexed { i, range -> list += Piece(p, i, range, endsParagraph = i == ranges.lastIndex) }
        }
        pieces = list
        firstPiece = first
    }

    private fun sentenceCount(paragraph: Int): Int =
        (if (paragraph + 1 < firstPiece.size) firstPiece[paragraph + 1] else pieces.size) - firstPiece[paragraph]

    /** The piece the state points at: the one being read, or where reading resumes. */
    private fun place(): Int {
        val s = _state.value
        if (s.paragraphs.isEmpty()) return 0
        return firstPiece[s.index] + s.sentence
    }

    /** This state with its place at [piece] (paragraph, sentence, whether it's the last). */
    private fun State.placedAt(piece: Int): State {
        val p = pieces.getOrNull(piece) ?: return copy(index = 0, sentence = 0, lastSentence = true)
        return copy(index = p.paragraph, sentence = p.sentence, lastSentence = piece == pieces.lastIndex)
    }

    /** Moves to [piece]; keeps reading if it was reading. */
    private fun jumpToPiece(piece: Int) {
        if (piece !in pieces.indices) return
        if (_state.value.status == Status.PLAYING) {
            startAt(piece)
        } else {
            // Not yet started stays "ready"; after reading, a jump pauses there.
            _state.update {
                it.placedAt(piece).copy(status = if (it.status == Status.READY) it.status else Status.PAUSED, problem = null)
            }
        }
    }

    private fun restartSentence() {
        if (_state.value.status == Status.PLAYING) startAt(place())
    }

    private fun startAt(piece: Int) {
        silence()
        queuedUpTo = piece - 1
        resumeOnFocusGain = false
        _state.update { it.placedAt(piece).copy(status = Status.PLAYING, problem = null, preparing = true) }
        val problem = enqueue(piece, flush = true) ?: return
        halt()
        _state.update { it.copy(status = Status.PAUSED, problem = problem, preparing = false) }
    }

    /**
     * Queues [piece] unless it already is (or doesn't exist); [ahead] while
     * the one before it plays (see Speaker.speak).
     * @return null when queued, else why it can't be spoken. For a piece
     *   queued ahead, a problem is acted on only when the one before it ends
     *   ([onDone]).
     */
    private fun enqueue(piece: Int, flush: Boolean, ahead: Boolean = false): Problem? {
        if (piece > pieces.lastIndex || piece <= queuedUpTo) return null
        val s = _state.value
        val p = pieces[piece]
        val paragraph = s.paragraphs[p.paragraph]
        // The language of the whole paragraph, as the voice choice shows it.
        val locale = localeFor(paragraph) ?: return Problem.NO_VOICE
        val text = paragraph.substring(p.range.first, p.range.last + 1)
        if (!speaker.speak(text, idOf(generation, piece), flush, s.rate, locale, ahead)) return Problem.ENGINE
        queuedUpTo = piece
        return null
    }

    /** Silences everything queued; callbacks of what was queued no longer count. */
    private fun silence() {
        generation++
        queuedUpTo = -1
        speaker.stop()
    }

    /** [silence], and gives the audio back. */
    private fun halt() {
        silence()
        guard.stop()
        resumeOnFocusGain = false
    }

    /**
     * The sleep timer ends within [SLEEP_LOOKAHEAD_MS]. Nothing is then
     * queued ahead across a paragraph's end: the engine starts a queued
     * sentence the moment the one before ends, so stopping there would cut
     * its first syllable off. The price is the engine's usual start-up pause
     * between paragraphs, in the last minutes only.
     */
    private fun sleepsSoon(): Boolean = _state.value.sleepAt?.let { clock() + SLEEP_LOOKAHEAD_MS >= it } ?: false

    private fun sleepDue(): Boolean = _state.value.sleepAt?.let { clock() >= it } ?: false

    /** The piece of a current-generation utterance id, else null. */
    private fun current(utteranceId: String): Int? {
        val match = ID.matchEntire(utteranceId) ?: return null
        if (match.groupValues[1].toInt() != generation) return null
        return match.groupValues[2].toInt().takeIf { it in pieces.indices }
    }

    companion object {
        private val ID = Regex("read-(\\d+)-(\\d+)")

        internal fun idOf(generation: Int, piece: Int) = "read-$generation-$piece"

        /** The speeds the reader offers. */
        val RATES = listOf(0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)

        /** The sleep timer's choices, in minutes. */
        val SLEEP_MINUTES = listOf(15, 30, 45, 60)

        /**
         * Longer than a paragraph takes to read at normal speed (1,000
         * characters, about 70 s), so the paragraph the timer ends in is
         * almost never already followed by a queued one.
         */
        internal const val SLEEP_LOOKAHEAD_MS = 2 * 60_000L
    }
}
