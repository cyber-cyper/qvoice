package com.riniso.qvoice.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.riniso.qvoice.BuildConfig
import com.riniso.qvoice.R

/**
 * Opens [url]: in the app [inPackage] if given and installed, else in
 * whatever handles it (the browser).
 * @return false if nothing could open it (no browser); the address is then
 *   on the screen to type.
 */
internal fun openUrl(context: Context, url: String, inPackage: String? = null): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (inPackage != null) {
        try {
            context.startActivity(Intent(intent).setPackage(inPackage))
            return true
        } catch (e: ActivityNotFoundException) {
            // Not installed (or disabled): the browser below.
        }
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/**
 * QVoice's own page in Android's settings (App info), where Battery and the
 * other per-app settings are. Every Android version has it; the battery page
 * itself has no public address.
 */
internal fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // A settings app without it (not seen on real phones): nothing to open.
    }
}

/**
 * The phone's text-to-speech settings (Preferred engine). The first intent
 * is the one Android's own Settings app exports for that page; it isn't
 * public API, so the accessibility settings (which contain it) and then the
 * settings' front page follow, and Help names the path to tap.
 */
internal fun openTtsSettings(context: Context) {
    val candidates = listOf(
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // try the next one
        }
    }
}

/**
 * QVoice's page on Google Play, in the Play Store app when there is one.
 * A plain link, as Google advises for a button the user taps; the in-app
 * review card is for prompts QVoice would show by itself, which it doesn't.
 */
internal fun openPlayListing(context: Context) {
    openUrl(context, Links.playListing(Links.PLAY_APP_ID), inPackage = Links.PLAY_STORE_PACKAGE)
}

/**
 * A new email to support in the user's mail app, subject and details
 * (FeedbackEmail) filled in. Nothing is sent until the user sends it.
 * "mailto:" with the address as an extra is the form Android's own guide
 * uses: only mail apps answer it.
 */
internal fun sendFeedback(context: Context) {
    val body = FeedbackEmail.body(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        preferredEngine = Settings.Secure.getString(context.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH),
        ownPackage = context.packageName,
    )
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(Links.SUPPORT_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, FeedbackEmail.subject(BuildConfig.VERSION_NAME))
        .putExtra(Intent.EXTRA_TEXT, body)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No mail app at all: the address, to write from anywhere.
        Toast.makeText(context, context.getString(R.string.feedback_no_email_app, Links.SUPPORT_EMAIL), Toast.LENGTH_LONG).show()
    }
}
