package com.riniso.qvoice.voices

import android.content.res.AssetManager
import java.io.IOException
import java.io.InputStream

/**
 * The APK's own assets. Besides the [AssetSource] calls, it hands sherpa-onnx
 * the [manager] itself, for the built-in voice that is read straight from the
 * APK (SherpaModelLoader).
 */
class ApkAssets(val manager: AssetManager) : AssetSource {

    override fun open(path: String): InputStream = manager.open(path)

    override fun length(path: String): Long =
        try {
            // Stored uncompressed (the model files, see isUncompressed): the
            // APK's index gives the length without reading anything.
            manager.openFd(path).use { it.length }
        } catch (e: IOException) {
            // Compressed or missing. An asset stream reports its whole
            // uncompressed size as available() until something is read.
            try {
                manager.open(path).use { it.available().toLong() }
            } catch (e: IOException) {
                -1L
            }
        }

    /**
     * True if [path] is stored uncompressed in the APK, as the model files must
     * be (app/build.gradle.kts, noCompress): sherpa-onnx then copies them
     * straight out of the memory-mapped APK, where a compressed one would first
     * be inflated in full on every load (for the built-in voice another 57 MB
     * of memory and a delay before the first word). Only an uncompressed asset
     * can be opened as a file descriptor; start-up logs the result (AppGraph).
     */
    fun isUncompressed(path: String): Boolean =
        try {
            manager.openFd(path).close()
            true
        } catch (e: IOException) {
            false
        }
}
