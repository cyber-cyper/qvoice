package com.riniso.qvoice.voices

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Opens files shipped inside the APK (`assets/`). Abstracted so the code that
 * uses them is JVM-testable; the app's implementation is [ApkAssets].
 */
fun interface AssetSource {
    fun open(path: String): InputStream

    /**
     * True if [path] names a file (not a folder) in the assets. The check the
     * engine makes before handing sherpa-onnx an asset path: sherpa-onnx ends
     * the whole process when an asset it is told to read is missing.
     */
    fun exists(path: String): Boolean =
        try {
            open(path).close()
            true
        } catch (e: IOException) {
            false
        }

    /**
     * The file's size in bytes, or -1 if it is missing. This default reads the
     * whole file; [ApkAssets] asks the APK's index instead.
     */
    fun length(path: String): Long =
        try {
            open(path).use { stream ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    total += n
                }
                total
            }
        } catch (e: IOException) {
            -1L
        }
}

/**
 * A voice shipped inside the APK so QVoice speaks on first launch, offline,
 * before the user has downloaded anything.
 *
 * @property assetDir the folder under `assets/` holding the files [manifest]
 *   names. sherpa-onnx reads them from there (the manifest itself is code, not
 *   a file).
 */
data class BundledVoice(val manifest: VoiceManifest, val assetDir: String)

/**
 * The voices shipped inside the APK, and the one thing they need copied out of
 * it: the shared eSpeak NG data.
 *
 * Why the voices aren't copied (since 1.0.0): given Android's AssetManager,
 * sherpa-onnx reads a voice's model, speaker table and tokens straight from
 * the APK. Earlier versions unpacked the built-in voice into `filesDir` on
 * first run: 60 MB of every phone's storage for bytes the APK already holds,
 * and a wait before the very first word. Those copies are deleted here.
 *
 * Why eSpeak NG's data still is copied: espeak-ng opens its files by path with
 * plain `fopen`, and APK assets have no path. There is no "install hook" on
 * Android; the first process start after install (or update) is the earliest
 * moment.
 *
 * Idempotent and thread-safe: the Application runs it in the background, and
 * the TTS service calls it again (blocking) if a synthesis request beats the
 * background run. A marker check makes repeat calls cheap.
 */
class BundledVoices(
    private val assets: AssetSource,
    private val paths: QVoicePaths,
    val declared: List<BundledVoice> = DEFAULT,
) {
    fun isBundled(voiceId: String): Boolean = declared.any { it.manifest.id == voiceId }

    /** A voice an earlier QVoice version shipped built in; its copy on disk is obsolete. */
    fun isRetired(voiceId: String): Boolean = voiceId in RETIRED_IDS && !isBundled(voiceId)

    /** True when the built-in voices can speak: the shared eSpeak NG data is on disk and current. */
    fun isReady(): Boolean {
        val marker = File(paths.espeakDataDir, ESPEAK_MARKER)
        return marker.isFile && runCatching { marker.readText().trim() }.getOrNull() == ESPEAK_DATA_VERSION
    }

    /**
     * Makes sure the shared eSpeak NG data is on disk and current, after
     * deleting obsolete copies: those of retired built-in voices, and the
     * copies that versions before 1.0.0 unpacked of today's built-in voices.
     * Throws [IOException] if the copy fails (for example the device is out
     * of storage); a later call retries from scratch.
     *
     * @return the bytes freed by deleting obsolete copies (0 once they're gone).
     */
    fun ensureInstalled(): Long =
        synchronized(paths.installLock) {
            // First, so their space is free before anything is copied.
            val obsolete = RETIRED_IDS.filter { isRetired(it) } + declared.map { it.manifest.id }
            val freed = obsolete.sumOf { deleteFolder(paths.voiceDir(it)) }
            ensureEspeakData()
            freed
        }

    private fun deleteFolder(dir: File): Long {
        if (!dir.exists()) return 0L
        val bytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        dir.deleteRecursively()
        return bytes
    }

    private fun ensureEspeakData() {
        if (isReady()) return
        val staging = paths.newStagingDir("espeak-ng-data")
        try {
            assets.open(ESPEAK_ASSET_ZIP).use { ZipExtractor.extract(it, staging) }
            // Sanity check before swapping in: espeak-ng refuses to start
            // without these, and a truncated zip would otherwise surface as
            // a native init failure on first synthesis.
            for (required in listOf("phontab", "phonindex", "phondata", "intonations", "en_dict")) {
                if (!File(staging, required).isFile) throw IOException("bundled espeak-ng data lacks $required")
            }
            File(staging, ESPEAK_MARKER).writeText(ESPEAK_DATA_VERSION)
            swapIntoPlace(staging, paths.espeakDataDir, paths.stagingRoot)
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw if (e is IOException) e else IOException("espeak-ng data copy failed", e)
        }
    }

    companion object {
        const val ESPEAK_ASSET_ZIP = "bundled/espeak-ng-data.zip"
        const val ESPEAK_MARKER = ".qvoice-data-version"

        /**
         * Identifies the espeak-ng data build: the sherpa-onnx release it was
         * taken from plus a hash of the whole tree. Bump it whenever the zip
         * in assets changes, so existing installs re-copy it.
         */
        const val ESPEAK_DATA_VERSION = "sherpa-onnx-1.13.8/3984dd9cb661a20c"

        /**
         * KittenTTS nano v0.8, full precision (Apache-2.0, KittenML), as
         * packaged by sherpa-onnx (tts-models release,
         * kitten-nano-en-v0_8-fp32.tar.bz2).
         *
         * Why this voice ships in the APK:
         * - a good small English model with 8 speakers (4 female, 4 male), so
         *   QVoice speaks — and offers a choice — before anything is downloaded;
         * - Apache-2.0, unlike Piper "amy"/"lessac" whose dataset licences
         *   are unclear or non-commercial.
         *
         * Why fp32 and not the int8 build slices 1-2 shipped (57 MB instead
         * of 24 MB): on a Galaxy M31 the int8 build generated at ~0.8x real
         * time — a 5 s silence after "Hello!" in the sample, and Stop waiting
         * for the sentence in progress. Its dynamically quantised layers
         * (ConvInteger) run slowly on CPUs without int8 dot-product
         * instructions, the phone's included. The fp32 build is 2.7x faster
         * on the same test (sandbox, 2 threads: RTF 0.106 vs 0.289); on the
         * M31 it measured 1.2-1.5x real time (0.3.0 logs), enough to keep up.
         * Same voices.bin and tokens.txt byte for byte, so the speaker mapping
         * below is unchanged.
         *
         * The id carries no build name so it can stay the same whatever build
         * ships (names are public API; see VoiceNames). The int8 build's id
         * is retired: its old copy is deleted ([RETIRED_IDS]) and names that
         * use it still resolve (VoiceNames.RENAMED).
         *
         * Speaker ids were mapped to upstream's names by comparing each row of
         * voices.bin with KittenML's named voice embeddings (bit-identical).
         * Order is best-first (the order Marmalade TTS settled on after
         * listening tests); the first speaker is the default.
         */
        val KITTEN_NANO_EN = VoiceManifest(
            schema = VoiceManifest.CURRENT_SCHEMA,
            id = "kitten-nano-en",
            family = VoiceFamily.KITTEN,
            displayName = "Kitten Nano",
            version = 1,
            sampleRate = 24_000,
            languages = listOf("en-US"),
            quality = VoiceQuality.NORMAL,
            files = VoiceFiles(model = "model.fp32.onnx", tokens = "tokens.txt", voices = "voices.bin"),
            espeakData = EspeakData.SHARED,
            speakers = listOf(
                Speaker(sid = 5, key = "rosie", displayName = "Rosie", gender = Gender.FEMALE),
                Speaker(sid = 2, key = "bruno", displayName = "Bruno", gender = Gender.MALE),
                Speaker(sid = 7, key = "kiki", displayName = "Kiki", gender = Gender.FEMALE),
                Speaker(sid = 4, key = "hugo", displayName = "Hugo", gender = Gender.MALE),
                Speaker(sid = 1, key = "bella", displayName = "Bella", gender = Gender.FEMALE),
                Speaker(sid = 0, key = "jasper", displayName = "Jasper", gender = Gender.MALE),
                Speaker(sid = 3, key = "luna", displayName = "Luna", gender = Gender.FEMALE),
                Speaker(sid = 6, key = "leo", displayName = "Leo", gender = Gender.MALE),
            ),
            defaultSpeakerKey = "rosie",
            license = LicenseInfo(
                name = "Apache-2.0",
                holder = "KittenML",
                url = "https://github.com/KittenML/KittenTTS",
            ),
            sourceUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-fp32.tar.bz2",
            sourceSha256 = "16092117bfe591ddcd58d078e1454603b8e1caea46f85653b2c2efae76bd883e",
        )

        /** Built-in voices of earlier versions whose copies are deleted on start-up. */
        val RETIRED_IDS: Set<String> = setOf("kitten-nano-en-v0_8-int8")

        /**
         * The files are put in assets/bundled/kitten-nano-en/ by
         * tools/get-binaries.ps1, from the archive named in
         * [KITTEN_NANO_EN].sourceUrl (checksums checked): LICENSE,
         * model.fp32.onnx, tokens.txt and voices.bin. The build refuses to run
         * without them (app/build.gradle.kts), BundledAssetsTest checks they
         * match the manifest, and the models are stored uncompressed in the
         * APK (noCompress), which is how sherpa-onnx reads them fastest.
         */
        val DEFAULT: List<BundledVoice> = listOf(
            BundledVoice(KITTEN_NANO_EN, "bundled/kitten-nano-en"),
        )
    }
}
