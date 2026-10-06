package com.riniso.qvoice.catalog

import com.riniso.qvoice.voices.VoiceManifestJson
import org.json.JSONObject

/**
 * Reads the voice catalogue (assets/catalog.json):
 *
 * ```
 * { "schema": 1, "version": 3,
 *   "voices": [ { "manifest": { ...same as qvoice-voice.json... },
 *                 "download": { "url": "https://...", "sha256": "...", "size": 123,
 *                               "format": "tar.bz2", "root": "folder-in-archive" },
 *                 "installedSize": 456, "summary": "...", "acceptance": "openrail-m",
 *                 "speedHint": 0.45 } ] }
 * ```
 *
 * "speedHint" is optional (older catalogues have none); when present it must
 * be a plausible relative speed ([MIN_SPEED_HINT]..[MAX_SPEED_HINT]).
 *
 * Strict: one bad entry rejects the whole catalogue. It ships inside the APK
 * and a unit test parses the real file, so a mistake fails the build's tests
 * instead of showing users a half-broken list.
 */
object CatalogJson {

    const val CURRENT_SCHEMA = 1
    const val ASSET = "catalog.json"

    /** Upper bound for one archive (the largest today, Kokoro, is 350 MB). */
    const val MAX_ARCHIVE_BYTES: Long = 2L * 1024 * 1024 * 1024

    /** Upper bound for one installed voice. */
    const val MAX_INSTALLED_BYTES: Long = 4L * 1024 * 1024 * 1024

    /** Relative speeds outside this range are typos, not voices. */
    const val MIN_SPEED_HINT = 0.01
    const val MAX_SPEED_HINT = 20.0

    private val SHA256 = Regex("[0-9a-f]{64}")

    class InvalidCatalogException(message: String) : Exception(message)

    fun parse(text: String): Catalog {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw InvalidCatalogException("not a JSON object: ${e.message}")
        }
        val schema = root.optInt("schema", -1)
        if (schema < 1) throw InvalidCatalogException("missing schema")
        if (schema > CURRENT_SCHEMA) throw InvalidCatalogException("schema $schema is newer than $CURRENT_SCHEMA")
        val version = root.optInt("version", 0)
        if (version < 1) throw InvalidCatalogException("missing version")
        val voices = root.optJSONArray("voices") ?: throw InvalidCatalogException("missing voices")

        val entries = ArrayList<CatalogEntry>(voices.length())
        val seen = HashSet<String>()
        for (i in 0 until voices.length()) {
            val o = voices.optJSONObject(i) ?: throw InvalidCatalogException("voice $i is not an object")
            val entry = parseEntry(o, i)
            if (!seen.add(entry.id)) throw InvalidCatalogException("duplicate voice id '${entry.id}'")
            entries += entry
        }
        return Catalog(version, entries)
    }

    private fun parseEntry(o: JSONObject, index: Int): CatalogEntry {
        val manifestObj = o.optJSONObject("manifest") ?: throw InvalidCatalogException("voice $index has no manifest")
        val manifest = try {
            VoiceManifestJson.parseObject(manifestObj)
        } catch (e: VoiceManifestJson.InvalidManifestException) {
            throw InvalidCatalogException("voice $index: ${e.message}")
        }
        val id = manifest.id
        val d = o.optJSONObject("download") ?: throw InvalidCatalogException("$id: no download")
        val url = str(d, "url") ?: throw InvalidCatalogException("$id: no download url")
        // https only: the checksum protects integrity, TLS keeps what people
        // download private on shared networks.
        if (!url.startsWith("https://") || url.any { it.isWhitespace() }) {
            throw InvalidCatalogException("$id: download url must be https")
        }
        val sha256 = str(d, "sha256") ?: throw InvalidCatalogException("$id: no sha256")
        if (!SHA256.matches(sha256)) throw InvalidCatalogException("$id: sha256 must be 64 lowercase hex digits")
        val size = d.optLong("size", 0L)
        if (size !in 1..MAX_ARCHIVE_BYTES) throw InvalidCatalogException("$id: bad download size $size")
        val formatKey = str(d, "format")
        val format = ArchiveFormat.fromKey(formatKey) ?: throw InvalidCatalogException("$id: unknown format '$formatKey'")
        val archiveRoot = str(d, "root")?.let {
            try {
                VoiceManifestJson.safeName(it).trimEnd('/')
            } catch (e: VoiceManifestJson.InvalidManifestException) {
                throw InvalidCatalogException("$id: ${e.message}")
            }
        }

        val installedSize = o.optLong("installedSize", 0L)
        if (installedSize !in 1..MAX_INSTALLED_BYTES) throw InvalidCatalogException("$id: bad installedSize $installedSize")
        val summary = str(o, "summary") ?: throw InvalidCatalogException("$id: no summary")
        val acceptance = str(o, "acceptance")
        if (acceptance != null && !Licences.isKnown(acceptance)) {
            throw InvalidCatalogException("$id: unknown licence to accept '$acceptance'")
        }
        val speedHint = if (o.has("speedHint") && !o.isNull("speedHint")) {
            val v = o.optDouble("speedHint", Double.NaN)
            if (v.isNaN() || v < MIN_SPEED_HINT || v > MAX_SPEED_HINT) {
                throw InvalidCatalogException("$id: bad speedHint ${o.opt("speedHint")}")
            }
            v.toFloat()
        } else {
            null
        }
        return CatalogEntry(
            manifest = manifest,
            download = DownloadInfo(url, sha256, size, format, archiveRoot),
            installedSize = installedSize,
            summary = summary,
            acceptance = acceptance,
            speedHint = speedHint,
        )
    }

    // Same null handling as VoiceManifestJson (Android's optString returns "null").
    private fun str(o: JSONObject, key: String): String? {
        if (o.isNull(key)) return null
        return o.optString(key, "").ifEmpty { null }
    }
}
