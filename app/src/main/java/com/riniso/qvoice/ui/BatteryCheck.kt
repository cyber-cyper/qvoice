package com.riniso.qvoice.ui

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

/**
 * Whether Android holds QVoice under battery restrictions: the most common
 * reason reading aloud (in QVoice's reader, or in another app speaking with
 * QVoice) stops when the screen turns off (D-053). Home then shows a card
 * with the way to its settings; otherwise nothing, so nobody is nagged.
 *
 * Two signals, both readable by the app itself without a permission:
 * - "Restricted" in the app's battery settings (and the background limits
 *   some phone makers put on "deep sleeping" apps), which is the
 *   run-in-background app op that ActivityManager.isBackgroundRestricted
 *   reports (Android 9+);
 * - the restricted app standby bucket Android 11+ puts apps in on its own.
 * QVoice can't lift either itself: asking for an exemption from battery
 * optimisation is restricted by Play's policy to other kinds of apps, and an
 * unrestricted app doesn't need one.
 */
object BatteryCheck {

    /** UsageStatsManager.STANDBY_BUCKET_RESTRICTED (API 30), by value: the API it is read from is 28. */
    const val BUCKET_RESTRICTED = 45

    fun isRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val backgroundRestricted = context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        val bucket = context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket
        return restricted(backgroundRestricted, bucket)
    }

    /** The rule, apart for the unit tests. Buckets: 10 active … 40 rare, 45 restricted. */
    fun restricted(backgroundRestricted: Boolean, standbyBucket: Int?): Boolean =
        backgroundRestricted || (standbyBucket != null && standbyBucket >= BUCKET_RESTRICTED)
}
