package com.riniso.qvoice.settings

import android.content.Context
import android.content.SharedPreferences
import com.riniso.qvoice.engine.SpeedBook
import com.riniso.qvoice.engine.ThreadPolicy
import com.riniso.qvoice.service.VoiceDefaults

/**
 * User choices that the TTS service reads on its hot path.
 *
 * SharedPreferences rather than DataStore on purpose: reads are synchronous
 * and served from memory after the first load, so the service's binder and
 * synthesis threads never block on I/O or `runBlocking` (the pattern that
 * pushed Marmalade towards Android's synthesis watchdog). The UI and the
 * service run in the same process, so a write is visible to the next request
 * immediately.
 *
 * Backed up to the user's Google account (see res/xml/backup_rules.xml); the
 * voices themselves are not, because they are large and re-downloadable.
 * Settings that only make sense on this phone (the CPU thread count) live in
 * a second file that is not backed up, so a new phone starts on "automatic".
 */
class QVoiceSettings(context: Context) : VoiceDefaults, SpeedBook.Store {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val devicePrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(DEVICE_FILE_NAME, Context.MODE_PRIVATE)

    /**
     * CPU threads the voices run with; 0 = automatic (ThreadPolicy.autoThreads).
     * Read at every model load; a change reloads each voice the next time it
     * speaks (EngineHost compares the loader's signature).
     */
    var engineThreads: Int
        get() = devicePrefs.getInt(KEY_ENGINE_THREADS, 0).coerceIn(0, ThreadPolicy.MAX_THREADS)
        set(value) {
            devicePrefs.edit().putInt(KEY_ENGINE_THREADS, value.coerceIn(0, ThreadPolicy.MAX_THREADS)).apply()
        }

    override fun defaultVoiceFor(languageKey: String): String? =
        prefs.getString(KEY_DEFAULT_VOICE_PREFIX + languageKey, null)

    /** @param languageKey "en" or "en-IN"; [voiceName] null clears the choice. */
    fun setDefaultVoice(languageKey: String, voiceName: String?) {
        prefs.edit().apply {
            val key = KEY_DEFAULT_VOICE_PREFIX + languageKey
            if (voiceName == null) remove(key) else putString(key, voiceName)
        }.apply()
    }

    /**
     * Makes [voiceName] the default for its language tag ("en-GB") and for its
     * language as a whole ("en"): apps asking for British English get it, and
     * so does any English request without a more specific choice (an en-IN
     * phone, an app that sends no region). An earlier choice for another
     * region of the same language ("en-US") keeps applying to that region.
     * Home's "Make default" and the reader's voice choice both come here.
     */
    fun makeDefault(voiceName: String, languageTag: String, language: String) {
        if (languageTag != language) setDefaultVoice(languageTag, voiceName)
        setDefaultVoice(language, voiceName)
    }

    /** Measured voice speeds (SpeedBook): per phone, so in the not-backed-up file. */
    override fun loadSpeeds(): Map<String, Float> =
        devicePrefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_SPEED_PREFIX) && value is Float) key.removePrefix(KEY_SPEED_PREFIX) to value else null
        }.toMap()

    override fun saveSpeed(voiceId: String, factor: Float) {
        devicePrefs.edit().putFloat(KEY_SPEED_PREFIX + voiceId, factor).apply()
    }

    /** The read-aloud speed (reader screen); a listening habit, so it follows the user to a new phone. */
    var readerRate: Float
        get() = prefs.getFloat(KEY_READER_RATE, 1f).takeIf { it.isFinite() && it in 0.25f..6f } ?: 1f
        set(value) {
            prefs.edit().putFloat(KEY_READER_RATE, value).apply()
        }

    /** Licences with use restrictions (see catalog.Licences) the user has accepted. */
    fun hasAccepted(licence: String): Boolean = prefs.getBoolean(KEY_ACCEPTED_PREFIX + licence, false)

    fun accept(licence: String) {
        prefs.edit().putBoolean(KEY_ACCEPTED_PREFIX + licence, true).apply()
    }

    companion object {
        /** Also named in res/xml/backup_rules.xml and data_extraction_rules.xml. */
        const val FILE_NAME = "qvoice_settings"

        /** Not named in the backup rules on purpose (they list what IS backed up). */
        const val DEVICE_FILE_NAME = "qvoice_device"
        private const val KEY_DEFAULT_VOICE_PREFIX = "default_voice."
        private const val KEY_ACCEPTED_PREFIX = "accepted_licence."
        private const val KEY_ENGINE_THREADS = "engine_threads"
        private const val KEY_SPEED_PREFIX = "speed."
        private const val KEY_READER_RATE = "reader_rate"
    }
}
