package com.riniso.qvoice.ui

/**
 * QVoice's addresses outside the app, in one place. The source repository is
 * not here: it is `qvoice.sourceUrl` in gradle.properties
 * (BuildConfig.SOURCE_CODE_URL), because the release build checks it.
 *
 * The privacy policy lives on the publisher's own domain, next to QTune's,
 * rather than on GitHub Pages: a link inside an installed app can never be
 * changed again, and a domain Riniso owns outlives any repository name or
 * account (D-049). Its text is store/privacy.html.
 */
object Links {
    const val PRIVACY_POLICY = "https://zinijo.com/qvoice/privacy.html"

    const val SUPPORT_EMAIL = "support@zinijo.com"

    /** The Play Store app, which opens [playListing] links itself. */
    const val PLAY_STORE_PACKAGE = "com.android.vending"

    /**
     * QVoice's id on Google Play. Not the running app's package name: debug
     * builds are com.riniso.qvoice.debug (app/build.gradle.kts), which has no
     * Play page.
     */
    const val PLAY_APP_ID = "com.riniso.qvoice"

    /**
     * The https address rather than market://: Google's recommended form,
     * which the Play Store app opens directly (with [PLAY_STORE_PACKAGE] set
     * on the intent) and any browser can open too.
     */
    fun playListing(packageName: String) = "https://play.google.com/store/apps/details?id=$packageName"
}

/**
 * The start of a feedback email (About → Send feedback). The mail app shows
 * it before anything is sent, so the user sees, and can delete, every detail.
 * Only what support needs to reproduce a problem: the versions, the phone
 * model and which engine the phone uses. Never any text QVoice has read.
 * English on purpose: it is for whoever answers, in any language.
 */
object FeedbackEmail {

    fun subject(versionName: String) = "QVoice feedback ($versionName)"

    /**
     * @param preferredEngine the package name of the phone's preferred
     *   text-to-speech engine (Settings.Secure.TTS_DEFAULT_SYNTH), null if
     *   unknown; QVoice's own [ownPackage] is shown as "QVoice".
     */
    fun body(
        versionName: String,
        versionCode: Int,
        androidRelease: String,
        sdkInt: Int,
        manufacturer: String,
        model: String,
        preferredEngine: String?,
        ownPackage: String,
    ): String = buildString {
        val engine = preferredEngine?.trim()?.takeIf { it.isNotEmpty() }
        // Two empty lines first: mail apps put the cursor at the top, so the
        // message is written above the details, not after them.
        append("\n\n")
        append(DIVIDER).append('\n')
        append("QVoice ").append(versionName).append(" (").append(versionCode).append(")\n")
        append("Android ").append(androidRelease).append(" (API ").append(sdkInt).append(")\n")
        append("Phone: ").append(deviceName(manufacturer, model)).append('\n')
        append("Preferred engine: ").append(
            when (engine) {
                null -> "unknown"
                ownPackage -> "QVoice"
                else -> engine
            },
        ).append('\n')
    }

    /**
     * "Samsung SM-M315F", "Google Pixel 8": the maker, then the model, but
     * the maker only once when the model name already starts with it
     * (Motorola reports "motorola edge 40").
     */
    fun deviceName(manufacturer: String, model: String): String {
        val maker = manufacturer.trim()
        val name = model.trim()
        return when {
            maker.isEmpty() -> name
            name.isEmpty() -> maker.replaceFirstChar { it.titlecase() }
            name.startsWith(maker, ignoreCase = true) -> name
            else -> maker.replaceFirstChar { it.titlecase() } + " " + name
        }
    }

    /** Separates the user's message from the details below it. */
    const val DIVIDER = "--- Details for QVoice support ---"
}
