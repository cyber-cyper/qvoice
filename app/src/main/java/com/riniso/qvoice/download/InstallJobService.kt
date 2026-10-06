package com.riniso.qvoice.download

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobWorkItem
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.riniso.qvoice.AppGraph
import com.riniso.qvoice.QVoiceApp

/**
 * Installs finished downloads. Unpacking a large voice takes a minute or two
 * on a phone — far longer than a broadcast receiver may run — and must
 * survive the user leaving the app, so it runs as a JobScheduler job (no
 * foreground service needed).
 *
 * One job id, many work items: each finished download is enqueued as a
 * JobWorkItem, so a second download finishing mid-install queues behind the
 * first instead of cancelling it (which re-scheduling the same job id would
 * do). Work that is interrupted (Android stops the job) is not completed, so
 * JobScheduler hands it back when the job runs again; the download file is
 * kept until an install succeeds or fails for good.
 */
class InstallJobService : JobService() {

    @Volatile
    private var stopped = false

    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        val library = (application as QVoiceApp).graph.library
        // The thread holds `params` for the whole job (Android 16 counts a
        // collected JobParameters as an abandoned job).
        Thread({ work(library, params) }, "qvoice-install").start()
        return true
    }

    private fun work(library: VoiceLibrary, params: JobParameters) {
        while (!stopped) {
            val item = try {
                params.dequeueWork()
            } catch (e: Exception) {
                // The job was stopped between our check and the call.
                null
            } ?: return // Queue empty: Android finishes the job itself.
            val voiceId = item.intent.getStringExtra(EXTRA_VOICE_ID)
            val started = SystemClock.elapsedRealtime()
            val outcome = if (voiceId == null) {
                VoiceLibrary.InstallOutcome.NOTHING_TO_DO
            } else {
                try {
                    library.installDownloaded(voiceId) { stopped }
                } catch (t: Throwable) {
                    Log.e(AppGraph.TAG, "Install of $voiceId crashed", t)
                    VoiceLibrary.InstallOutcome.FAILED
                }
            }
            // With the time taken: how long a phone needs to unpack each voice
            // is what decides whether zip mirrors are worth it (BACKLOG).
            Log.i(AppGraph.TAG, "Install $voiceId: $outcome in ${SystemClock.elapsedRealtime() - started} ms")
            // Not completed = redelivered when the rescheduled job runs.
            if (outcome == VoiceLibrary.InstallOutcome.RETRY_LATER) return
            try {
                params.completeWork(item)
            } catch (e: Exception) {
                return
            }
        }
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        return true // reschedule whatever is left
    }

    companion object {
        private const val JOB_ID = 0x51564F // "QVO"
        private const val EXTRA_VOICE_ID = "com.riniso.qvoice.VOICE_ID"

        fun schedule(context: Context, voiceId: String) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, InstallJobService::class.java))
                // No network needed; run as soon as allowed.
                .setOverrideDeadline(0)
                .build()
            val result = scheduler.enqueue(job, JobWorkItem(Intent().putExtra(EXTRA_VOICE_ID, voiceId)))
            if (result != JobScheduler.RESULT_SUCCESS) Log.w(AppGraph.TAG, "Could not schedule install of $voiceId")
        }
    }
}
