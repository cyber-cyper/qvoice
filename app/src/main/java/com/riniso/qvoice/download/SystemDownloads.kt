package com.riniso.qvoice.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.riniso.qvoice.R
import java.io.File

/**
 * [Downloads] backed by Android's DownloadManager.
 *
 * Why DownloadManager and not our own HTTP code: it resumes after network
 * loss and reboots, waits for Wi-Fi when asked, follows GitHub's redirect to
 * its file CDN, shows progress in the notification shade, and keeps going
 * while QVoice isn't running — without a foreground service, which Play
 * would want justified for a 350 MB download.
 *
 * Files land in the app's own folder on shared storage
 * (Android/data/com.riniso.qvoice/files/voice-downloads): no storage
 * permission is needed, other apps can't see them, and uninstalling removes
 * them. Each file is deleted as soon as its voice is installed.
 */
class SystemDownloads(context: Context) : Downloads {

    private val appContext = context.applicationContext
    private val manager: DownloadManager? = appContext.getSystemService(DownloadManager::class.java)

    override fun folder(): File? = appContext.getExternalFilesDir(DIR_TYPE)

    override fun enqueue(url: String, fileName: String, title: String, allowMetered: Boolean): Long {
        val dm = manager ?: throw IllegalStateException("DownloadManager unavailable")
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription(appContext.getString(R.string.download_notification_description))
            // Progress in the shade while running; nothing left behind when done
            // (the file isn't something to open — QVoice installs it).
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(appContext, DIR_TYPE, fileName)
            .setAllowedOverMetered(allowMetered)
            .setAllowedOverRoaming(false)
        return dm.enqueue(request)
    }

    override fun query(ids: Collection<Long>): Map<Long, DownloadProgress> {
        val dm = manager ?: return emptyMap()
        if (ids.isEmpty()) return emptyMap()
        val out = HashMap<Long, DownloadProgress>()
        dm.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val reasonCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
            val soFarCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            while (c.moveToNext()) {
                val reason = c.getInt(reasonCol)
                out[c.getLong(idCol)] = DownloadProgress(
                    phase = DownloadStates.phaseOf(c.getInt(statusCol), reason),
                    downloaded = c.getLong(soFarCol),
                    total = c.getLong(totalCol),
                    reason = reason,
                )
            }
        }
        return out
    }

    override fun localFile(id: Long): File? {
        val dm = manager ?: return null
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return null
            val uri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)) ?: return null
            return Uri.parse(uri).path?.let(::File)
        }
        return null
    }

    override fun remove(id: Long) {
        manager?.remove(id)
    }

    companion object {
        const val DIR_TYPE = "voice-downloads"
    }
}
