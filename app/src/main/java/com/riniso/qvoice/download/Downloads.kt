package com.riniso.qvoice.download

import android.app.DownloadManager
import java.io.File

/** Where a download stands, in the terms the voice library shows. */
enum class DownloadPhase {
    QUEUED,
    RUNNING,
    WAITING_FOR_NETWORK,
    /** The user chose "wait for Wi-Fi" (or the download is too big for mobile data). */
    WAITING_FOR_WIFI,
    /** DownloadManager will try again by itself (server hiccup, connection lost). */
    RETRYING,
    DONE,
    FAILED,
}

data class DownloadProgress(
    val phase: DownloadPhase,
    val downloaded: Long,
    /** -1 until the server has said how big the file is. */
    val total: Long,
    /** DownloadManager's COLUMN_REASON: why it is paused or failed. */
    val reason: Int,
)

/** Why a voice couldn't be downloaded or installed; shown until dismissed or retried. */
enum class Problem {
    NO_SPACE,
    NETWORK,
    /** The file is no longer on the server (HTTP 404/410): this QVoice's catalogue is out of date. */
    GONE,
    /** Shared storage (where downloads land) is missing or unwritable. */
    NO_STORAGE,
    /** The download is not the file the catalogue validated. */
    CHECKSUM,
    INSTALL,
}

/**
 * The part of Android's DownloadManager the voice library uses, behind an
 * interface so the library's state machine is unit-tested with a fake.
 * Implemented by [SystemDownloads].
 */
interface Downloads {
    /** The folder downloads land in, or null when shared storage is unavailable. */
    fun folder(): File?

    /** Starts a download into [folder]/[fileName]; returns DownloadManager's id for it. */
    fun enqueue(url: String, fileName: String, title: String, allowMetered: Boolean): Long

    /** Current state of each id DownloadManager still knows (unknown ids are left out). */
    fun query(ids: Collection<Long>): Map<Long, DownloadProgress>

    /** Where a finished download's file is. */
    fun localFile(id: Long): File?

    /** Cancels a download, or deletes a finished one's file. */
    fun remove(id: Long)
}

/** DownloadManager status codes to library terms. Pure, so it is unit-tested. */
object DownloadStates {

    fun phaseOf(status: Int, reason: Int): DownloadPhase = when (status) {
        DownloadManager.STATUS_PENDING -> DownloadPhase.QUEUED
        DownloadManager.STATUS_RUNNING -> DownloadPhase.RUNNING
        DownloadManager.STATUS_PAUSED -> when (reason) {
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> DownloadPhase.WAITING_FOR_WIFI
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> DownloadPhase.WAITING_FOR_NETWORK
            else -> DownloadPhase.RETRYING
        }
        DownloadManager.STATUS_SUCCESSFUL -> DownloadPhase.DONE
        else -> DownloadPhase.FAILED
    }

    /** For a FAILED download, [reason] is an ERROR_* constant or the HTTP status. */
    fun problemOf(reason: Int): Problem = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> Problem.NO_SPACE
        DownloadManager.ERROR_DEVICE_NOT_FOUND, DownloadManager.ERROR_FILE_ERROR -> Problem.NO_STORAGE
        404, 410 -> Problem.GONE
        else -> Problem.NETWORK
    }
}
