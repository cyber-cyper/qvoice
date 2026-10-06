package com.riniso.qvoice.voices

import com.riniso.qvoice.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The built-in voice's files in app/src/main/assets match what the code hands
 * sherpa-onnx. Nothing else checks this before a phone does: the paths come
 * from BundledVoices, the files from tools/get-binaries.ps1, and
 * sherpa-onnx ends the whole process when a path it is given is missing.
 * (The build's own check, in app/build.gradle.kts, lists the files by name,
 * so it can't notice the code asking for a different one.)
 */
class BundledAssetsTest {

    private fun assetDir(voice: BundledVoice): File = TestFiles.appFile("src/main/assets/${voice.assetDir}")

    @Test
    fun everyFileTheManifestNamesIsInTheAssets() {
        for (voice in BundledVoices.DEFAULT) {
            val dir = assetDir(voice)
            for (name in voice.manifest.requiredFiles()) {
                val file = File(dir, name)
                assertTrue("${voice.assetDir}/$name is missing (run tools/get-binaries.ps1)", file.isFile && file.length() > 0)
            }
            // The model's licence travels with it (Apache-2.0, section 4).
            assertTrue("${voice.assetDir}/LICENSE is missing", File(dir, "LICENSE").isFile)
        }
    }

    /** Everything under assets/ is packed into the APK, used or not. */
    @Test
    fun nothingUnusedIsPackedIntoTheApk() {
        for (voice in BundledVoices.DEFAULT) {
            val expected = voice.manifest.requiredFiles().toSet() + "LICENSE"
            assertEquals(expected, assetDir(voice).list().orEmpty().toSet())
        }
        // Earlier versions shipped the voice as a zip; only the eSpeak NG data is one now.
        val zips = TestFiles.appFile("src/main/assets/bundled").list().orEmpty().filter { it.endsWith(".zip") }
        assertEquals(listOf(File(BundledVoices.ESPEAK_ASSET_ZIP).name), zips)
    }
}
