package com.riniso.qvoice.reader

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.riniso.qvoice.AppGraph
import com.riniso.qvoice.QVoiceApp
import com.riniso.qvoice.R
import com.riniso.qvoice.ui.ReaderActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Listening with the screen off, or while in another app. For as long as a
 * listening session lasts (see ReadAloud), this foreground service of type
 * mediaPlayback keeps QVoice running, shows the reading notification (which
 * is also the lock screen's and Quick Settings' player) and holds the media
 * session that headset, Bluetooth and Android 13+ media controls talk to.
 * It holds no reading state: it mirrors [ReadAloud] through [NowPlaying] and
 * hands every button straight back to it.
 *
 * Lifetime: AppGraph starts it when reading starts (NowPlaying.startsService);
 * from then on it follows the reader and stops itself when the session ends:
 * Stop, new text, the reader closed while not reading, or [LONG_PAUSE_MS]
 * of pause. It stays in the foreground while paused rather than leaving and
 * coming back: Android 12+ refuses to put a service back in the foreground
 * from the background (a pause on the lock screen, a phone call), so leaving
 * at every pause would make Play in the notification unreliable. Media3 does
 * the same since 1.6 ("keep foreground service state for an additional 10
 * minutes when playback pauses").
 *
 * Main thread only, like ReadAloud: the lifecycle, the session's callbacks
 * (given the main handler), the notification's buttons (a receiver) and the
 * state collection all run there.
 */
class ReadAloudService : Service() {

    private val reader: ReadAloud by lazy { (application as QVoiceApp).graph.readAloud }
    private val main = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private var following: Job? = null
    private lateinit var notifications: NotificationManager
    private lateinit var session: MediaSession
    private lateinit var wakeLock: PowerManager.WakeLock

    /** The logo: the notification's picture, and the artwork of the lock screen's player. */
    private val logo: Bitmap? by lazy { BitmapFactory.decodeResource(resources, R.drawable.qvoice_logo) }

    /** What the notification and the session show; null until the first update. */
    private var shown: NowPlaying? = null
    private var foreground = false
    private var ended = false
    private var failed = false

    private val endLongPause = Runnable {
        Log.i(AppGraph.TAG, "Read aloud: not reading for ${LONG_PAUSE_MS / 60_000} minutes, session ended")
        reader.stop()
    }

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        createChannel()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
        session = MediaSession(this, SESSION_TAG).apply {
            setCallback(Controls(), main)
            setSessionActivity(openReader())
            // Android 13+ players: previous and next keep their places (next
            // is offered only while there is a next paragraph), so Stop never
            // slides into an empty slot under the user's thumb.
            setExtras(
                Bundle().apply {
                    putBoolean(RESERVE_PREVIOUS_SLOT, true)
                    putBoolean(RESERVE_NEXT_SLOT, true)
                },
            )
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val now = NowPlaying.of(reader.state.value)
        // startForegroundService() obliges a startForeground() now, whatever
        // the reader did in the meantime. If its session has already ended,
        // the service goes into the foreground once and leaves (update → end).
        if (now == null || ended) post(notification(now))
        shown = null
        update(now)
        // Collected only from here on, so nothing can end the service before
        // that obligation is met.
        if (following == null && !ended) {
            following = scope.launch { reader.state.collect { update(NowPlaying.of(it)) } }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // QVoice swiped away in Recents: like any audio app it reads on if it
        // is reading (the notification's Stop ends that); else the session ends.
        if (reader.state.value.status != ReadAloud.Status.PLAYING) reader.stop()
    }

    override fun onDestroy() {
        scope.cancel()
        main.removeCallbacks(endLongPause)
        if (wakeLock.isHeld) wakeLock.release()
        session.release()
        requested = false
        super.onDestroy()
        // Reading started again while this was stopping (Play right after
        // Stop): start afresh. Not after a failure, which would only repeat.
        if (!failed && NowPlaying.startsService(reader.state.value)) startIfNeeded(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun update(now: NowPlaying?) {
        if (ended) return
        if (now == null) {
            end()
            return
        }
        if (now == shown) return
        shown = now
        session.setPlaybackState(playbackState(now))
        session.setMetadata(metadata(now))
        main.removeCallbacks(endLongPause)
        if (now.playing) {
            // The voice may compute more slowly than the audio plays (a slow
            // voice, a high speed), and the phone must not fall asleep in such
            // a gap with the screen off. Renewed at every paragraph, so it
            // can't outlast a session that got stuck.
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        } else {
            if (wakeLock.isHeld) wakeLock.release()
            main.postDelayed(endLongPause, LONG_PAUSE_MS)
        }
        // Last: if it fails, end() undoes the above.
        post(notification(now))
    }

    /**
     * Shows [notification]: the first time by going into the foreground with
     * it. Without POST_NOTIFICATIONS on purpose: notifications of a media
     * session are exempt from it (Android 13+), so lint's warning is moot.
     */
    @SuppressLint("NotificationPermission")
    private fun post(notification: Notification) {
        if (foreground) {
            notifications.notify(NOTIFICATION_ID, notification)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
        } catch (e: RuntimeException) {
            // ForegroundServiceStartNotAllowedException (an IllegalStateException)
            // or a SecurityException. Not expected: reading only starts with
            // QVoice in front. Reading goes on, without the notification.
            Log.w(AppGraph.TAG, "Read aloud: couldn't show the notification", e)
            failed = true
            end()
        }
    }

    /** The session is over (or can't be shown): let go of everything and stop. */
    private fun end() {
        ended = true
        main.removeCallbacks(endLongPause)
        if (wakeLock.isHeld) wakeLock.release()
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        stopSelf()
    }

    /**
     * Title "Read aloud", then where it is or why it stopped; never the text
     * (see NowPlaying). Null [now]: a placeholder for a session that ended
     * before the service started.
     */
    private fun notification(now: NowPlaying?): Notification {
        val style = Notification.MediaStyle().setMediaSession(session.sessionToken)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_qvoice)
            .setColor(BRAND_COLOR)
            .setContentTitle(getString(R.string.reader_title))
            .setContentIntent(openReader())
            // Swiping the notification away (when Android allows it) is a Stop.
            .setDeleteIntent(broadcast(ACTION_STOP))
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
        logo?.let { builder.setLargeIcon(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (now != null) {
            // Android 12 and older draw the player from these buttons; always
            // four, in the same places (a next with nothing after it does nothing).
            builder.setContentText(describe(now))
                .addAction(button(R.drawable.ic_skip_previous, R.string.reader_previous, ACTION_PREVIOUS))
                .addAction(
                    if (now.playing) {
                        button(R.drawable.ic_pause, R.string.reader_pause, ACTION_PAUSE)
                    } else {
                        button(R.drawable.ic_play, R.string.reader_play, ACTION_PLAY)
                    },
                )
                .addAction(button(R.drawable.ic_skip_next, R.string.reader_next, ACTION_NEXT))
                .addAction(button(R.drawable.ic_close, R.string.action_stop, ACTION_STOP))
            style.setShowActionsInCompactView(0, 1, 2)
        }
        return builder.setStyle(style).build()
    }

    /** What Android 13+ players, headsets and other controllers see and may ask for. */
    private fun playbackState(now: NowPlaying): PlaybackState {
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (now.hasNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        return PlaybackState.Builder()
            .setState(
                if (now.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                if (now.playing) 1f else 0f,
            )
            .setActions(actions)
            .addCustomAction(
                PlaybackState.CustomAction.Builder(CUSTOM_ACTION_STOP, getString(R.string.action_stop), R.drawable.ic_close).build(),
            )
            .build()
    }

    private fun metadata(now: NowPlaying): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, getString(R.string.reader_title))
            .putString(MediaMetadata.METADATA_KEY_ARTIST, describe(now))
        logo?.let { builder.putBitmap(MediaMetadata.METADATA_KEY_ART, it) }
        return builder.build()
    }

    private fun describe(now: NowPlaying): String = when (now.problem) {
        ReadAloud.Problem.NO_VOICE -> getString(R.string.reader_notice_no_voice)
        ReadAloud.Problem.ENGINE -> getString(R.string.reader_notice_engine)
        ReadAloud.Problem.AUDIO_BUSY -> getString(R.string.reader_notice_audio)
        null -> {
            val where = if (now.finished) getString(R.string.reader_finished) else getString(R.string.reader_notice_position, now.paragraph, now.paragraphs)
            now.sleepAt?.let { getString(R.string.reader_notice_sleep, where, formatSleepTime(this, it)) } ?: where
        }
    }

    private fun button(icon: Int, label: Int, action: String): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, icon), getString(label), broadcast(action)).build()

    private fun broadcast(action: String): PendingIntent = PendingIntent.getBroadcast(
        this,
        0,
        Intent(this, ReadAloudActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    /** The reader (one instance, in its own task: see the manifest). */
    private fun openReader(): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(this, ReaderActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    /**
     * Low importance, as for any media player: no sound, no pop-up. Public on
     * the lock screen: it shows no text, only the position.
     */
    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.reader_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.reader_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notifications.createNotificationChannel(channel)
    }

    /** Headset and Bluetooth buttons (the session's default media-button handling calls these) and Android 13+ players. */
    private inner class Controls : MediaSession.Callback() {
        override fun onPlay() = reader.play()
        override fun onPause() = reader.pause()
        override fun onSkipToNext() = reader.next()
        override fun onSkipToPrevious() = reader.previous()
        override fun onStop() = reader.stop()
        override fun onCustomAction(action: String, extras: Bundle?) {
            if (action == CUSTOM_ACTION_STOP) reader.stop()
        }
    }

    companion object {
        const val ACTION_PLAY = "com.riniso.qvoice.reader.PLAY"
        const val ACTION_PAUSE = "com.riniso.qvoice.reader.PAUSE"
        const val ACTION_PREVIOUS = "com.riniso.qvoice.reader.PREVIOUS"
        const val ACTION_NEXT = "com.riniso.qvoice.reader.NEXT"
        const val ACTION_STOP = "com.riniso.qvoice.reader.STOP"

        private const val CUSTOM_ACTION_STOP = "com.riniso.qvoice.reader.STOP_SESSION"
        private const val CHANNEL_ID = "reading"
        private const val NOTIFICATION_ID = 1
        private const val SESSION_TAG = "QVoice read aloud"
        private const val WAKE_LOCK_TAG = "QVoice:ReadAloud"

        /** Paused this long, the session ends (the notification goes, the engine is let go). */
        private const val LONG_PAUSE_MS = 10 * 60_000L

        /** Longer than any paragraph takes even with the slowest voice, renewed at each. */
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60_000L

        /** androidx.media's SESSION_EXTRAS_KEY_SLOT_RESERVATION_* (the platform has no constant). */
        private const val RESERVE_PREVIOUS_SLOT = "android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS"
        private const val RESERVE_NEXT_SLOT = "android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT"

        /** The logo's indigo (primary in ui/Theme.kt): the notification's accent. */
        private const val BRAND_COLOR = 0xFF4C3FE0.toInt()

        /** From the start request until onDestroy; main thread only. */
        private var requested = false

        /**
         * Starts the service unless it runs or is starting. AppGraph calls it
         * whenever the reader reads (NowPlaying.startsService).
         */
        fun startIfNeeded(context: Context) {
            if (requested) return
            requested = true
            try {
                context.startForegroundService(Intent(context, ReadAloudService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (Android 12+), if
                // QVoice were in the background, which it isn't when reading
                // starts. Reading goes on; the next paragraph tries again.
                requested = false
                Log.w(AppGraph.TAG, "Read aloud: background listening couldn't start", e)
            }
        }
    }
}

/**
 * The notification's buttons, and its dismissal. Android 12 and older build
 * the player (Quick Settings, lock screen) from these buttons; newer versions
 * use the media session instead (ReadAloudService.Controls). Not exported:
 * only QVoice's own notification sends these.
 */
class ReadAloudActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reader = (context.applicationContext as QVoiceApp).graph.readAloud
        when (intent.action) {
            ReadAloudService.ACTION_PLAY -> reader.play()
            ReadAloudService.ACTION_PAUSE -> reader.pause()
            ReadAloudService.ACTION_PREVIOUS -> reader.previous()
            ReadAloudService.ACTION_NEXT -> reader.next()
            ReadAloudService.ACTION_STOP -> reader.stop()
        }
    }
}
