package com.riniso.qvoice.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** ReaderText, ReaderLanguage and the ReadAloud state machine (fake speaker and audio focus). */
class ReaderTest {

    // ---- ReaderText ----

    @Test
    fun blankLinesSeparateParagraphsAndHardWrapsAreJoined() {
        val text = "First line\nwrapped here.\n\n  \nSecond paragraph.\r\n\r\nThird\tone."
        assertEquals(
            listOf("First line wrapped here.", "Second paragraph.", "Third one."),
            ReaderText.prepare(text).paragraphs,
        )
    }

    @Test
    fun separatorsAndControlCharactersAreDropped() {
        val text = "Intro.\n\n* * *\n\n---\n\nEnd\u0007 here."
        assertEquals(listOf("Intro.", "End here."), ReaderText.prepare(text).paragraphs)
        assertTrue(ReaderText.prepare(" \n\n\t").paragraphs.isEmpty())
    }

    @Test
    fun longParagraphsAreDividedAtSentenceEnds() {
        val sentence = "This sentence has exactly fifty characters in it. "
        val paragraph = sentence.repeat(50).trim() // 2,499 characters
        val pieces = ReaderText.prepare(paragraph).paragraphs
        assertTrue(pieces.size >= 3)
        assertTrue(pieces.all { it.length <= ReaderText.MAX_PARAGRAPH && it.endsWith(".") })
        assertEquals(paragraph, pieces.joinToString(" "))
    }

    @Test
    fun hugeTextsAreCutAndSaySo() {
        val prepared = ReaderText.prepare("word ".repeat(ReaderText.MAX_CHARS))
        assertTrue(prepared.truncated)
        assertTrue(prepared.paragraphs.sumOf { it.length } <= ReaderText.MAX_CHARS)
        assertFalse(ReaderText.prepare("short").truncated)
        // Never half of a character outside the Basic Multilingual Plane.
        val emoji = "a".repeat(ReaderText.MAX_CHARS - 1) + "😀" + "b"
        assertFalse(ReaderText.prepare(emoji).paragraphs.last().last().isHighSurrogate())
    }

    @Test
    fun aSnippetIsWholeWordsAndSaysWhenItIsCut() {
        assertEquals("Short and sweet.", ReaderText.snippet("  Short and sweet.  "))
        val long = "The quick brown fox jumps over the lazy dog while the cat watches from the old fence post."
        val cut = ReaderText.snippet(long, maxChars = 40)
        assertEquals("The quick brown fox jumps over the lazy…", cut)
        assertTrue(cut.length <= 41)
        // The limit falls inside "dog": the cut goes back to the word before.
        assertEquals("The quick brown fox jumps over the lazy…", ReaderText.snippet(long, maxChars = 42))
        // No space to cut at: cut anyway, never half a character.
        assertFalse(ReaderText.snippet("a".repeat(39) + "😀" + "b".repeat(20), maxChars = 40).dropLast(1).last().isHighSurrogate())
    }

    // ---- ReaderLanguage ----

    private val india = Locale.forLanguageTag("en-IN")
    private val voices = setOf("en", "hi", "ja", "zh")

    @Test
    fun thePhonesLocaleWhenItsVoiceCanReadTheText() {
        assertEquals(india, ReaderLanguage.pick("Good morning, everyone.", india, voices))
        assertEquals(india, ReaderLanguage.pick("12:30 - 45%", india, voices)) // no script at all
    }

    @Test
    fun aVoiceOfTheTextsScriptOtherwise() {
        assertEquals("hi", ReaderLanguage.pick("नमस्ते, आप कैसे हैं?", india, voices)?.language)
        assertEquals("ja", ReaderLanguage.pick("こんにちは、元気ですか。", india, voices)?.language)
        assertEquals("zh", ReaderLanguage.pick("今天天气很好。", india, voices)?.language)
        // Latin text on a Hindi phone goes to English, not to the Hindi voice.
        assertEquals("en", ReaderLanguage.pick("See you tomorrow.", Locale.forLanguageTag("hi-IN"), voices)?.language)
        // A phone language without a voice (Tamil) falls back by script too.
        assertEquals("en", ReaderLanguage.pick("See you tomorrow.", Locale.forLanguageTag("ta-IN"), voices)?.language)
    }

    @Test
    fun noLocaleWhenNoVoiceReadsTheScript() {
        assertNull(ReaderLanguage.pick("வணக்கம், எப்படி இருக்கிறீர்கள்?", india, voices))
    }

    // ---- ReadAloud ----

    private data class Call(val text: String, val id: String, val flush: Boolean, val rate: Float, val locale: Locale)

    private class FakeSpeaker : Speaker {
        override var listener: Speaker.Listener? = null
        val calls = ArrayList<Call>()
        var stops = 0
        var shutdowns = 0
        var voiceChanges = 0
        var refuse = false
        override fun speak(text: String, utteranceId: String, flush: Boolean, rate: Float, locale: Locale): Boolean {
            if (refuse) return false
            calls += Call(text, utteranceId, flush, rate, locale)
            return true
        }
        override fun stop() {
            stops++
        }
        override fun shutdown() {
            shutdowns++
        }
        override fun voicesChanged() {
            voiceChanges++
        }
    }

    private class FakeGuard : PlaybackGuard {
        var granted = true
        var active = false
        var listener: PlaybackGuard.Listener? = null
        override fun start(listener: PlaybackGuard.Listener): Boolean {
            if (!granted) return false
            this.listener = listener
            active = true
            return true
        }
        override fun stop() {
            active = false
        }
    }

    private val speaker = FakeSpeaker()
    private val guard = FakeGuard()
    private val savedRates = ArrayList<Float>()
    private var unreadable: String? = null
    /** The reader's clock (ms), moved by hand. */
    private var now = 0L
    private val reader = ReadAloud(
        speaker,
        guard,
        localeFor = { p -> if (p == unreadable) null else Locale.US },
        onRateChanged = { savedRates += it },
        clock = { now },
    )
    private val text = "One.\n\nTwo.\n\nThree.\n\nFour."

    private fun lastIdOf(paragraph: String) = speaker.calls.last { it.text == paragraph }.id

    /** Paragraph [paragraph] begins playing (the engine's onStart). */
    private fun started(paragraph: String) = reader.onStart(lastIdOf(paragraph))

    @Test
    fun eachParagraphQueuesTheNextWhenItStarts() {
        reader.load(text, play = true)
        assertEquals(ReadAloud.Status.PLAYING, reader.state.value.status)
        assertEquals(listOf("One." to true), speaker.calls.map { it.text to it.flush })
        assertTrue(guard.active)

        started("One.")
        assertEquals(listOf("One." to true, "Two." to false), speaker.calls.map { it.text to it.flush })
        started("Two.")
        assertEquals(1, reader.state.value.index)
        assertEquals("Three." to false, speaker.calls.last().let { it.text to it.flush })
        started("Two.") // repeated callbacks queue nothing twice
        assertEquals(1, speaker.calls.count { it.text == "Three." })
    }

    @Test
    fun callbacksOfDroppedUtterancesAreIgnored() {
        reader.load(text, play = true)
        started("One.")
        val oldTwo = lastIdOf("Two.")
        reader.jumpTo(3)
        assertEquals(3, reader.state.value.index)
        reader.onStart(oldTwo) // arrives late, after the jump
        assertEquals(3, reader.state.value.index)
        reader.onDone(oldTwo)
        assertEquals(ReadAloud.Status.PLAYING, reader.state.value.status)
    }

    @Test
    fun pauseKeepsThePlaceAndPlayResumesThere() {
        reader.load(text, play = true)
        started("One.")
        started("Two.")
        reader.pause()
        assertEquals(ReadAloud.Status.PAUSED, reader.state.value.status)
        assertFalse(guard.active)
        reader.play()
        assertEquals("Two.", speaker.calls.last { it.flush }.text)
        assertEquals(ReadAloud.Status.PLAYING, reader.state.value.status)
    }

    @Test
    fun theEndFinishesAndPlayStartsOver() {
        reader.load("Only one.", play = true)
        reader.onDone(lastIdOf("Only one."))
        assertEquals(ReadAloud.Status.FINISHED, reader.state.value.status)
        assertFalse(guard.active)
        reader.play()
        assertEquals(2, speaker.calls.count { it.text == "Only one." })
    }

    @Test
    fun aParagraphNoVoiceCanReadStopsReadingThereAndSaysWhy() {
        unreadable = "Three."
        reader.load(text, play = true)
        started("One.")
        started("Two.") // "Three." can't be queued
        reader.onDone(lastIdOf("Two."))
        assertEquals(2, reader.state.value.index)
        assertEquals(ReadAloud.Status.PAUSED, reader.state.value.status)
        assertEquals(ReadAloud.Problem.NO_VOICE, reader.state.value.problem)
        // Skipping it reads on.
        reader.next()
        assertEquals(3, reader.state.value.index)
        reader.play()
        assertEquals("Four.", speaker.calls.last().text)
        assertNull(reader.state.value.problem)
    }

    @Test
    fun busyAudioMeansNoReading() {
        guard.granted = false
        reader.load(text, play = true)
        assertTrue(speaker.calls.isEmpty())
        assertEquals(ReadAloud.Problem.AUDIO_BUSY, reader.state.value.problem)
        assertEquals(ReadAloud.Status.READY, reader.state.value.status)
    }

    @Test
    fun aCallPausesAndReadingResumesAfterIt() {
        reader.load(text, play = true)
        started("One.")
        started("Two.")
        guard.listener!!.onPauseRequested(resumeLater = true)
        assertEquals(ReadAloud.Status.PAUSED, reader.state.value.status)
        assertTrue("still holding the audio request, for the callback", guard.active)
        guard.listener!!.onResumeAllowed()
        assertEquals(ReadAloud.Status.PLAYING, reader.state.value.status)
        assertEquals("Two.", speaker.calls.last { it.flush }.text)
    }

    @Test
    fun unpluggedHeadphonesPauseForGood() {
        reader.load(text, play = true)
        val listener = guard.listener!!
        listener.onPauseRequested(resumeLater = false)
        assertEquals(ReadAloud.Status.PAUSED, reader.state.value.status)
        assertFalse(guard.active)
        listener.onResumeAllowed()
        assertEquals(ReadAloud.Status.PAUSED, reader.state.value.status)
    }

    @Test
    fun aNewSpeedIsHeardAtOnceAndRemembered() {
        reader.load(text, play = true)
        started("One.")
        started("Two.")
        reader.setRate(2f)
        val restart = speaker.calls.last { it.flush }
        assertEquals("Two." to 2f, restart.text to restart.rate)
        assertEquals(listOf(2f), savedRates)
        assertEquals(2f, reader.state.value.rate, 0f)
    }

    @Test
    fun engineErrorsPauseWithTheReason() {
        reader.load(text, play = true)
        reader.onError(lastIdOf("One."), -3)
        assertEquals(ReadAloud.Status.PAUSED, reader.state.value.status)
        assertEquals(ReadAloud.Problem.ENGINE, reader.state.value.problem)
        speaker.refuse = true
        reader.play()
        assertEquals(ReadAloud.Problem.ENGINE, reader.state.value.problem)
        assertEquals(ReadAloud.Status.PAUSED, reader.state.value.status)
    }

    @Test
    fun stopEndsTheSessionButKeepsTheTextAndThePlace() {
        reader.load(text, play = true)
        started("One.")
        started("Two.")
        val two = lastIdOf("Two.")
        reader.stop()
        assertEquals(ReadAloud.Status.READY, reader.state.value.status)
        assertEquals(1, reader.state.value.index)
        assertEquals(4, reader.state.value.paragraphs.size)
        assertEquals(1, speaker.shutdowns)
        assertFalse(guard.active)
        reader.onDone(two) // a late callback from before the stop changes nothing
        assertEquals(ReadAloud.Status.READY, reader.state.value.status)
        // Play goes on where it was.
        reader.play()
        assertEquals("Two." to true, speaker.calls.last().let { it.text to it.flush })
    }

    @Test
    fun stopAfterTheEndStartsOverAndStopWithNothingIsHarmless() {
        reader.stop()
        assertEquals(ReadAloud.Status.EMPTY, reader.state.value.status)
        reader.load("A.\n\nB.", play = true)
        started("A.")
        reader.onDone(lastIdOf("A."))
        reader.onDone(lastIdOf("B."))
        assertEquals(ReadAloud.Status.FINISHED, reader.state.value.status)
        reader.stop()
        assertEquals(ReadAloud.Status.READY, reader.state.value.status)
        assertEquals(0, reader.state.value.index)
    }

    // ---- A remembered text (ReaderMemory) ----

    @Test
    fun aRememberedTextComesBackReadyAtItsPlaceWithoutSpeaking() {
        reader.setRate(1.5f)
        reader.restore(listOf("One.", "Two.", "Three."), index = 2, truncated = true)
        val s = reader.state.value
        assertEquals(ReadAloud.Status.READY, s.status)
        assertEquals(2, s.index)
        assertTrue(s.truncated)
        assertEquals(1.5f, s.rate, 0f)
        // Nothing is said unasked, and the audio isn't taken.
        assertTrue(speaker.calls.isEmpty())
        assertFalse(guard.active)
        // Play goes on from the remembered paragraph.
        reader.play()
        assertEquals("Three.", speaker.calls.single().text)
    }

    @Test
    fun aRestoreNeverReplacesATextThatCameFirst() {
        reader.load(text, play = false)
        reader.restore(listOf("Old."), index = 0, truncated = false)
        assertEquals(listOf("One.", "Two.", "Three.", "Four."), reader.state.value.paragraphs)
        // Nor does an empty memory fill an empty reader.
        reader.load("", play = false)
        reader.restore(emptyList(), index = 0, truncated = false)
        assertEquals(ReadAloud.Status.EMPTY, reader.state.value.status)
    }

    @Test
    fun aRememberedPlaceOutsideTheTextIsClamped() {
        reader.restore(listOf("One.", "Two."), index = 7, truncated = false)
        assertEquals(1, reader.state.value.index)
    }

    // ---- The voice choice ----

    @Test
    fun anotherVoiceIsHeardAtOnceFromTheParagraphsStart() {
        reader.load(text, play = true)
        started("One.")
        started("Two.")
        reader.voiceChanged()
        assertEquals(1, speaker.voiceChanges)
        assertEquals("Two." to true, speaker.calls.last().let { it.text to it.flush })
        assertEquals(ReadAloud.Status.PLAYING, reader.state.value.status)
        // Paused, the new voice is simply used from the next Play on.
        reader.pause()
        val before = speaker.calls.size
        reader.voiceChanged()
        assertEquals(2, speaker.voiceChanges)
        assertEquals(before, speaker.calls.size)
    }

    @Test
    fun theVoiceChoiceListsTheLanguageOfTheParagraph() {
        reader.load(text, play = false)
        assertEquals(Locale.US, reader.localeOf(0))
        unreadable = "Two."
        assertNull(reader.localeOf(1))
        assertNull(reader.localeOf(9))
    }

    // ---- The sleep timer ----

    @Test
    fun theSleepTimerStopsBetweenParagraphsOnceItsTimeIsUp() {
        reader.load(text, play = true)
        reader.setSleepTimer(15)
        val end = 15 * 60_000L
        assertEquals(end, reader.state.value.sleepAt)

        started("One.") // far from the end: the next is queued ahead as usual
        assertEquals(1, speaker.calls.count { it.text == "Two." })
        reader.onDone(lastIdOf("One."))
        now = end - 60_000 // the last minutes: nothing more is queued ahead...
        started("Two.")
        assertEquals(0, speaker.calls.count { it.text == "Three." })
        now = end - 30_000 // ...but reading goes on, one paragraph at a time
        reader.onDone(lastIdOf("Two."))
        assertEquals("Three." to false, speaker.calls.last().let { it.text to it.flush })
        started("Three.")
        assertEquals(0, speaker.calls.count { it.text == "Four." })

        now = end + 5_000 // time is up during "Three.": it's read to its end, then reading stops
        reader.onDone(lastIdOf("Three."))
        val stopped = reader.state.value
        assertEquals(ReadAloud.Status.PAUSED, stopped.status)
        assertEquals("Play goes on with the next paragraph", 3, stopped.index)
        assertNull(stopped.sleepAt)
        assertFalse(guard.active)
        assertEquals(0, speaker.calls.count { it.text == "Four." })
        reader.play()
        assertEquals("Four." to true, speaker.calls.last().let { it.text to it.flush })
    }

    @Test
    fun aTimerThatRanOutWhilePausedDoesntStopTheNextStart() {
        reader.load(text, play = true)
        reader.setSleepTimer(15)
        started("One.")
        reader.pause()
        now = 20 * 60_000L
        reader.play()
        assertNull(reader.state.value.sleepAt)
        started("One.")
        assertEquals("queued ahead again", "Two.", speaker.calls.last().text)
    }

    @Test
    fun theTimerEndsWithTheTextOrWhenTurnedOff() {
        reader.load("A.", play = true)
        reader.setSleepTimer(30)
        reader.setSleepTimer(null)
        assertNull(reader.state.value.sleepAt)
        reader.setSleepTimer(30)
        reader.onDone(lastIdOf("A."))
        assertEquals(ReadAloud.Status.FINISHED, reader.state.value.status)
        assertNull(reader.state.value.sleepAt)
        reader.setSleepTimer(30)
        reader.stop()
        assertNull(reader.state.value.sleepAt)
    }

    // ---- NowPlaying (the notification and the media session) ----

    @Test
    fun theNotificationExistsOnlyDuringAListeningSession() {
        assertNull(NowPlaying.of(reader.state.value)) // nothing loaded
        reader.load(text, play = false)
        assertNull(NowPlaying.of(reader.state.value)) // loaded, not started
        assertFalse(NowPlaying.startsService(reader.state.value))

        reader.play()
        assertTrue(NowPlaying.startsService(reader.state.value))
        assertEquals(NowPlaying(playing = true, paragraph = 1, paragraphs = 4, finished = false, problem = null), NowPlaying.of(reader.state.value))
        reader.setSleepTimer(45)
        assertEquals("the notification shows when it stops", 45 * 60_000L, NowPlaying.of(reader.state.value)?.sleepAt)
        started("One.")
        started("Two.")
        reader.pause()
        val paused = NowPlaying.of(reader.state.value)!!
        assertFalse(paused.playing)
        assertEquals(2, paused.paragraph)
        assertTrue(paused.hasNext)
        assertFalse(NowPlaying.startsService(reader.state.value))

        reader.stop()
        assertNull(NowPlaying.of(reader.state.value))
    }

    @Test
    fun theNotificationSaysWhenReadingIsDoneOrWhyItStopped() {
        unreadable = "Two."
        reader.load(text, play = true)
        reader.onDone(lastIdOf("One.")) // "Two." has no voice
        val stuck = NowPlaying.of(reader.state.value)!!
        assertEquals(ReadAloud.Problem.NO_VOICE, stuck.problem)
        assertEquals(2, stuck.paragraph)

        unreadable = null
        reader.jumpTo(3)
        reader.play()
        reader.onDone(lastIdOf("Four."))
        val done = NowPlaying.of(reader.state.value)!!
        assertTrue(done.finished)
        assertFalse(done.playing)
        assertFalse("nothing after the last paragraph", done.hasNext)
        assertNull(done.problem)
    }

    /** A Play that can't read anything brings up no notification. */
    @Test
    fun aPlayThatFailsAtOnceStartsNoService() {
        unreadable = "One."
        reader.load(text, play = true)
        assertEquals(ReadAloud.Status.PAUSED, reader.state.value.status)
        assertFalse(NowPlaying.startsService(reader.state.value))
    }

    @Test
    fun newTextReplacesTheOldAndKeepsTheSpeed() {
        reader.setRate(1.5f)
        reader.load(text, play = true)
        reader.load("Something else.", play = false)
        assertEquals(listOf("Something else."), reader.state.value.paragraphs)
        assertEquals(ReadAloud.Status.READY, reader.state.value.status)
        assertEquals(1.5f, reader.state.value.rate, 0f)
        assertFalse(guard.active)
    }

    /**
     * With TalkBack on, Android's TTS service would make TalkBack wait for
     * whatever the reader has queued: short pieces, one at a time.
     */
    @Test
    fun aScreenReaderGetsATurnAfterEveryPiece() {
        val talkBackSpeaker = FakeSpeaker()
        val withTalkBack = ReadAloud(talkBackSpeaker, FakeGuard(), localeFor = { Locale.US }, screenReaderOn = { true })
        val long = "This sentence has exactly fifty characters in it. ".repeat(20).trim()
        withTalkBack.load("$long\n\nLast.", play = true)
        val pieces = withTalkBack.state.value.paragraphs
        assertTrue(pieces.size > 3)
        assertTrue(pieces.all { it.length <= ReaderText.SCREEN_READER_PARAGRAPH })

        withTalkBack.onStart(talkBackSpeaker.calls.single().id)
        assertEquals("nothing queued ahead", 1, talkBackSpeaker.calls.size)
        withTalkBack.onDone(talkBackSpeaker.calls.single().id)
        assertEquals(2, talkBackSpeaker.calls.size)
        assertEquals(pieces[1] to false, talkBackSpeaker.calls.last().let { it.text to it.flush })
        assertEquals(ReadAloud.Status.PLAYING, withTalkBack.state.value.status)
    }
}
