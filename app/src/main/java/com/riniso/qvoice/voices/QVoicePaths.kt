package com.riniso.qvoice.voices

import java.io.File

/**
 * Every on-disk location QVoice uses, in one place.
 *
 * All of it lives under the app's private `filesDir` so that staging folders
 * and final folders sit on the same filesystem: installs are made atomic by
 * `File.renameTo`, which only works within one filesystem.
 *
 * ```
 * files/
 *   voices/<voiceId>/            one folder per downloaded voice (built-in
 *                                voices are read from the APK's assets)
 *       qvoice-voice.json        written last; no manifest = not installed
 *   shared/espeak-ng-data/       the single espeak-ng data copy (see EspeakData.SHARED)
 *       .qvoice-data-version     marker naming which data build is present
 *   staging/                     half-written installs; wiped on start-up
 * ```
 */
class QVoicePaths(val filesDir: File) {
    val voicesDir: File = File(filesDir, "voices")
    val sharedDir: File = File(filesDir, "shared")
    val espeakDataDir: File = File(sharedDir, "espeak-ng-data")
    val stagingRoot: File = File(filesDir, "staging")

    /**
     * Held by every installer for the whole of an install (extract, verify,
     * swap) and by [clearStaging]. Without it, start-up cleanup could delete
     * a staging folder that the first synthesis request is filling right now
     * (the eSpeak NG data copy can start from the TTS service before the
     * background cleanup has run). Voice downloads must take it too.
     */
    val installLock = Any()

    fun voiceDir(voiceId: String): File = File(voicesDir, voiceId)

    /** A fresh, empty staging folder for one install attempt. */
    fun newStagingDir(label: String): File {
        stagingRoot.mkdirs()
        var dir: File
        do {
            dir = File(stagingRoot, "$label-${System.nanoTime()}")
        } while (dir.exists())
        dir.mkdirs()
        return dir
    }

    /** Removes leftovers of installs that were interrupted (crash, reboot, kill). */
    fun clearStaging() {
        synchronized(installLock) {
            stagingRoot.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}

/**
 * Replaces [target] with the fully written [staged] folder as close to
 * atomically as a filesystem allows: the old folder is first renamed aside,
 * then the new one renamed into place, then the old one deleted. If the
 * process dies midway, [target] is either the old voice, the new voice, or
 * missing — never a mix — and a missing manifest simply means "reinstall".
 */
internal fun swapIntoPlace(staged: File, target: File, stagingRoot: File) {
    target.parentFile?.mkdirs()
    var trash: File? = null
    if (target.exists()) {
        stagingRoot.mkdirs()
        val aside = File(stagingRoot, "trash-${target.name}-${System.nanoTime()}")
        if (!target.renameTo(aside)) {
            // Rename can fail on exotic storage; fall back to deleting in place.
            target.deleteRecursively()
        } else {
            trash = aside
        }
    }
    if (!staged.renameTo(target)) {
        throw java.io.IOException("could not move ${staged.name} into ${target.path}")
    }
    trash?.deleteRecursively()
}
