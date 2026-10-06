package com.riniso.qvoice.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.riniso.qvoice.QVoiceApp

/**
 * DownloadManager's "download finished" broadcast (sent to QVoice's package
 * only). Hands over to the library on a short-lived thread: the lookup is
 * IPC, and the shared background thread may be busy with a model load.
 *
 * The broadcast is only a hint. The library re-reads the real state from
 * DownloadManager, so a missed broadcast is caught by the next refresh (app
 * start or the voice screen) and a spoofed one changes nothing.
 */
class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id < 0) return
        val library = (context.applicationContext as QVoiceApp).graph.library
        val pending = goAsync()
        Thread({
            try {
                library.onDownloadComplete(id)
            } finally {
                pending.finish()
            }
        }, "qvoice-download-done").start()
    }
}
