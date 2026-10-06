package com.riniso.qvoice.reader

/**
 * What the reading notification, the lock screen and the media session show
 * for the reader's state: whether it reads, where it is, why it stopped.
 * Kept apart from ReadAloudService so the rules are unit-tested.
 *
 * Never any of the text. A lock screen is public, and the reader may be
 * reading an email or a message out loud; the listener hears the words
 * anyway, so the position is all the notification needs.
 */
data class NowPlaying(
    val playing: Boolean,
    /** 1-based. */
    val paragraph: Int,
    val paragraphs: Int,
    val finished: Boolean,
    /** Why reading stopped by itself, if it did. */
    val problem: ReadAloud.Problem?,
    /** When the sleep timer stops reading (ReadAloud.State.sleepAt), or null. */
    val sleepAt: Long? = null,
) {
    /** "Next" does something (the media controls leave it out otherwise). */
    val hasNext: Boolean get() = paragraph < paragraphs

    companion object {
        /**
         * Null outside a listening session (see ReadAloud): nothing loaded,
         * loaded but not started, or the session ended. The notification
         * and the media session exist only while this is not null.
         */
        fun of(state: ReadAloud.State): NowPlaying? = when (state.status) {
            ReadAloud.Status.EMPTY, ReadAloud.Status.READY -> null
            ReadAloud.Status.PLAYING, ReadAloud.Status.PAUSED, ReadAloud.Status.FINISHED -> NowPlaying(
                playing = state.status == ReadAloud.Status.PLAYING,
                paragraph = state.index + 1,
                paragraphs = state.paragraphs.size,
                finished = state.status == ReadAloud.Status.FINISHED,
                problem = state.problem,
                sleepAt = state.sleepAt,
            )
        }

        /**
         * Whether the background service must be started: only once reading
         * has actually started. A Play that fails at once (no voice for the
         * first paragraph, the audio taken) never brings up a notification
         * for a session that read nothing; once running, the service follows
         * [of] until the session ends.
         */
        fun startsService(state: ReadAloud.State): Boolean = state.status == ReadAloud.Status.PLAYING
    }
}
