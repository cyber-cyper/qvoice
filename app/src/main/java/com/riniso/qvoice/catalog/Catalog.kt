package com.riniso.qvoice.catalog

import com.riniso.qvoice.voices.VoiceManifest

/** Archive formats the installer can unpack. Only what the catalogue uses today. */
enum class ArchiveFormat(val key: String) {
    TAR_BZ2("tar.bz2");

    companion object {
        fun fromKey(key: String?): ArchiveFormat? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Where and how to fetch a voice.
 *
 * @property sha256 of the whole archive, lowercase hex. The installer refuses
 *   anything else, so a replaced or tampered upstream file fails loudly
 *   instead of installing an unvalidated voice.
 * @property root the folder inside the archive that holds the voice files
 *   (sherpa-onnx archives wrap everything in one folder named after the
 *   archive); null when the files are at the top level.
 */
data class DownloadInfo(
    val url: String,
    val sha256: String,
    val size: Long,
    val format: ArchiveFormat,
    val root: String?,
)

/**
 * One downloadable voice pack.
 *
 * @property manifest what gets written as qvoice-voice.json once installed
 *   (the installer adds the source url and checksum).
 * @property installedSize bytes on disk after install, not counting the
 *   espeak-ng data QVoice shares between voices. Used for the storage check
 *   and shown in the voice library.
 * @property summary one line for the voice library ("54 voices in 9 languages").
 * @property acceptance a licence the user must accept before downloading
 *   (see [Licences]); null when the licence needs no acceptance.
 * @property speedHint how fast the voice runs relative to the built-in voice
 *   (1.0 = as fast), measured on a phone (see tools/catalog/make_catalog.py);
 *   the app multiplies it by the built-in voice's measured speed to predict
 *   "speed on this phone" before download. Null when unknown.
 */
data class CatalogEntry(
    val manifest: VoiceManifest,
    val download: DownloadInfo,
    val installedSize: Long,
    val summary: String,
    val acceptance: String?,
    val speedHint: Float? = null,
) {
    val id: String get() = manifest.id
}

/**
 * The voices QVoice offers for download. Bundled in the APK (assets/catalog.json)
 * so it works offline and every entry has been validated with the real engine
 * before release (see docs/CATALOGUE.md); a refreshable online copy can come later.
 */
data class Catalog(val version: Int, val entries: List<CatalogEntry>) {
    fun find(voiceId: String): CatalogEntry? = entries.firstOrNull { it.id == voiceId }

    companion object {
        val EMPTY = Catalog(0, emptyList())
    }
}

/**
 * Licences a user has to accept before a voice is downloaded, because they
 * carry use restrictions that pass on to every user (OpenRAIL-M's
 * "use-based restrictions", Attachment A). Everything else in the catalogue
 * is Apache-2.0, MIT or public domain and needs no click-through.
 */
object Licences {
    const val OPENRAIL_M = "openrail-m"

    /** Acceptance key -> full text under assets/. */
    val TEXT_ASSETS = mapOf(OPENRAIL_M to "licenses/OpenRAIL-M.txt")

    fun isKnown(key: String): Boolean = key in TEXT_ASSETS

    /**
     * The licence text shown for a voice, by the licence name in its
     * manifest. Every name used in the catalogue must be here (a unit test
     * checks), so each downloadable voice carries its notice in the app.
     */
    fun textAssetFor(licenseName: String): String? = when (licenseName) {
        "Apache-2.0" -> "licenses/Apache-2.0.txt"
        "MIT" -> "licenses/MIT-piper-voices.txt"
        "OpenRAIL-M" -> "licenses/OpenRAIL-M.txt"
        else -> null
    }
}
