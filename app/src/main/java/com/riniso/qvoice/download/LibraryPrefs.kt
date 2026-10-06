package com.riniso.qvoice.download

import android.content.Context
import android.content.SharedPreferences

/**
 * The little the voice library must remember across process deaths: which
 * DownloadManager id belongs to which voice, and the last problem per voice.
 * Everything else (installed voices, download progress) is re-read from the
 * filesystem and DownloadManager.
 */
interface LibraryPrefs {
    fun downloadId(voiceId: String): Long?
    fun setDownloadId(voiceId: String, id: Long?)
    fun trackedDownloads(): Map<String, Long>
    fun problem(voiceId: String): Problem?
    fun setProblem(voiceId: String, problem: Problem?)
}

/**
 * SharedPreferences file "qvoice_downloads". Deliberately not in the backup
 * rules: download ids mean nothing on another phone.
 */
class SharedPrefsLibraryPrefs(context: Context) : LibraryPrefs {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun downloadId(voiceId: String): Long? =
        prefs.getLong(DOWNLOAD + voiceId, -1L).takeIf { it >= 0 }

    override fun setDownloadId(voiceId: String, id: Long?) {
        prefs.edit().apply { if (id == null) remove(DOWNLOAD + voiceId) else putLong(DOWNLOAD + voiceId, id) }.apply()
    }

    override fun trackedDownloads(): Map<String, Long> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(DOWNLOAD) && value is Long) key.removePrefix(DOWNLOAD) to value else null
        }.toMap()

    override fun problem(voiceId: String): Problem? =
        prefs.getString(PROBLEM + voiceId, null)?.let { name -> Problem.entries.firstOrNull { it.name == name } }

    override fun setProblem(voiceId: String, problem: Problem?) {
        prefs.edit().apply { if (problem == null) remove(PROBLEM + voiceId) else putString(PROBLEM + voiceId, problem.name) }.apply()
    }

    companion object {
        const val FILE_NAME = "qvoice_downloads"
        private const val DOWNLOAD = "download."
        private const val PROBLEM = "problem."
    }
}
