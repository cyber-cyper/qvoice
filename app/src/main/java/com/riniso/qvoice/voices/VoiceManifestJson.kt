package com.riniso.qvoice.voices

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads and writes [VoiceManifest] as JSON with org.json (built into Android,
 * so no serialization plugin or reflection is involved).
 *
 * Parsing is strict about what the engine needs (id, family, files, sample
 * rate) and lenient about everything cosmetic: a manifest that fails
 * validation is rejected as a whole, because loading a half-understood voice
 * is how native crashes happen.
 *
 * Note on org.json: Android's implementation and the JSON-java one used by
 * unit tests disagree on `optString` for an explicit JSON null (Android
 * returns the string "null"). [str] checks `isNull` first so both behave the
 * same way.
 */
object VoiceManifestJson {

    class InvalidManifestException(message: String) : Exception(message)

    fun parse(text: String): VoiceManifest {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw InvalidManifestException("not a JSON object: ${e.message}")
        }
        return parseObject(root)
    }

    /** Parses a manifest embedded in a larger document (the voice catalogue). */
    fun parseObject(root: JSONObject): VoiceManifest {
        val schema = root.optInt("schema", -1)
        if (schema < 1) throw InvalidManifestException("missing schema")
        if (schema > VoiceManifest.CURRENT_SCHEMA) {
            // Written by a newer QVoice. Refuse rather than guess at fields.
            throw InvalidManifestException("schema $schema is newer than ${VoiceManifest.CURRENT_SCHEMA}")
        }
        val id = required(root, "id")
        if (!isSafeId(id)) throw InvalidManifestException("unsafe voice id '$id'")
        val familyKey = required(root, "family")
        val family = VoiceFamily.fromKey(familyKey)
            ?: throw InvalidManifestException("unknown family '$familyKey'")
        val sampleRate = root.optInt("sampleRate", 0)
        if (sampleRate !in 8_000..96_000) throw InvalidManifestException("bad sampleRate $sampleRate")

        val filesObj = root.optJSONObject("files") ?: throw InvalidManifestException("missing files")
        val files = VoiceFiles(
            model = safeName(required(filesObj, "model")),
            tokens = str(filesObj, "tokens")?.let(::safeName),
            voices = str(filesObj, "voices")?.let(::safeName),
            lexicons = strings(filesObj.optJSONArray("lexicons")).map(::safeName),
            vocoder = str(filesObj, "vocoder")?.let(::safeName),
            dictDir = str(filesObj, "dictDir")?.let(::safeName),
            ruleFsts = strings(filesObj.optJSONArray("ruleFsts")).map(::safeName),
            extra = stringMap(filesObj.optJSONObject("extra")).mapValues { safeName(it.value) },
        )

        val languages = strings(root.optJSONArray("languages"))
        if (languages.isEmpty()) throw InvalidManifestException("no languages")

        val speakers = parseSpeakers(root.optJSONArray("speakers"))
        val licenseObj = root.optJSONObject("license")
        val license = LicenseInfo(
            name = licenseObj?.let { str(it, "name") } ?: "unknown",
            holder = licenseObj?.let { str(it, "holder") } ?: "",
            url = licenseObj?.let { str(it, "url") } ?: "",
        )
        val sourceObj = root.optJSONObject("source")
        val languageConfig = parseLanguageConfig(root.optJSONObject("languageConfig"))

        return VoiceManifest(
            schema = schema,
            id = id,
            family = family,
            displayName = str(root, "name") ?: id,
            version = root.optInt("version", 1),
            sampleRate = sampleRate,
            languages = languages,
            quality = VoiceQuality.fromKey(str(root, "quality")),
            files = files,
            espeakData = EspeakData.fromKey(str(root, "espeakData")),
            speakers = speakers,
            defaultSpeakerKey = str(root, "defaultSpeaker"),
            license = license,
            sourceUrl = sourceObj?.let { str(it, "url") },
            sourceSha256 = sourceObj?.let { str(it, "sha256") },
            languageConfig = languageConfig,
        )
    }

    fun toJson(m: VoiceManifest): String = toObject(m).toString(2)

    fun toObject(m: VoiceManifest): JSONObject {
        val files = JSONObject().apply {
            put("model", m.files.model)
            m.files.tokens?.let { put("tokens", it) }
            m.files.voices?.let { put("voices", it) }
            if (m.files.lexicons.isNotEmpty()) put("lexicons", JSONArray(m.files.lexicons))
            m.files.vocoder?.let { put("vocoder", it) }
            m.files.dictDir?.let { put("dictDir", it) }
            if (m.files.ruleFsts.isNotEmpty()) put("ruleFsts", JSONArray(m.files.ruleFsts))
            if (m.files.extra.isNotEmpty()) {
                val extra = JSONObject()
                for ((k, v) in m.files.extra) extra.put(k, v)
                put("extra", extra)
            }
        }
        val speakers = JSONArray()
        for (s in m.speakers) {
            speakers.put(
                JSONObject().apply {
                    put("sid", s.sid)
                    put("key", s.key)
                    put("name", s.displayName)
                    put("gender", s.gender.key)
                    if (s.languages.isNotEmpty()) put("languages", JSONArray(s.languages))
                    s.quality?.let { put("quality", it.key) }
                },
            )
        }
        val root = JSONObject().apply {
            put("schema", m.schema)
            put("id", m.id)
            put("family", m.family.key)
            put("name", m.displayName)
            put("version", m.version)
            put("sampleRate", m.sampleRate)
            put("languages", JSONArray(m.languages))
            put("quality", m.quality.key)
            put("files", files)
            put("espeakData", m.espeakData.key)
            put("speakers", speakers)
            m.defaultSpeakerKey?.let { put("defaultSpeaker", it) }
            put(
                "license",
                JSONObject().apply {
                    put("name", m.license.name)
                    put("holder", m.license.holder)
                    put("url", m.license.url)
                },
            )
            if (m.languageConfig.isNotEmpty()) {
                val lc = JSONObject()
                for ((tag, cfg) in m.languageConfig) {
                    lc.put(
                        tag,
                        JSONObject().apply {
                            cfg.espeakVoice?.let { put("espeakVoice", it) }
                            cfg.lexicons?.let { put("lexicons", JSONArray(it)) }
                            cfg.dictDir?.let { put("dictDir", it) }
                            cfg.ruleFsts?.let { put("ruleFsts", JSONArray(it)) }
                        },
                    )
                }
                put("languageConfig", lc)
            }
            if (m.sourceUrl != null || m.sourceSha256 != null) {
                put(
                    "source",
                    JSONObject().apply {
                        m.sourceUrl?.let { put("url", it) }
                        m.sourceSha256?.let { put("sha256", it) }
                    },
                )
            }
        }
        return root
    }

    /**
     * Voice ids become folder names and part of framework voice names, so
     * they are restricted to ASCII letters, digits, '-', '_' and '.': no '#'
     * (the voice-name separator), no path separators, no leading dot (hidden
     * staging folders start with one).
     */
    fun isSafeId(id: String): Boolean =
        id.isNotEmpty() && id.length <= 120 && !id.startsWith(".") &&
            id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' || it == '.' }

    /**
     * File names are relative to the voice folder. Nested paths are allowed
     * ("dict/jieba.dict.utf8") but never absolute or escaping with "..".
     */
    fun safeName(name: String): String {
        val n = name.trim()
        if (n.isEmpty() || n.startsWith("/") || n.startsWith("\\") || n.contains('\u0000') ||
            n.split('/', '\\').any { it == ".." }
        ) {
            throw InvalidManifestException("unsafe file name '$name'")
        }
        return n
    }

    private fun parseSpeakers(arr: JSONArray?): List<Speaker> {
        if (arr == null) return emptyList()
        val out = ArrayList<Speaker>(arr.length())
        val seenKeys = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = str(o, "key") ?: throw InvalidManifestException("speaker $i has no key")
            if (!isSafeId(key)) throw InvalidManifestException("unsafe speaker key '$key'")
            if (!seenKeys.add(key)) throw InvalidManifestException("duplicate speaker key '$key'")
            val sid = o.optInt("sid", -1)
            if (sid < 0) throw InvalidManifestException("speaker '$key' has no sid")
            // "language" (one tag) is the slice-1 spelling; "languages" the current one.
            val languages = strings(o.optJSONArray("languages")).ifEmpty { listOfNotNull(str(o, "language")) }
            out += Speaker(
                sid = sid,
                key = key,
                displayName = str(o, "name") ?: key,
                gender = Gender.fromKey(str(o, "gender")),
                languages = languages,
                quality = str(o, "quality")?.let { VoiceQuality.fromKey(it) },
            )
        }
        return out
    }

    private fun parseLanguageConfig(o: JSONObject?): Map<String, LanguageConfig> {
        if (o == null) return emptyMap()
        val out = LinkedHashMap<String, LanguageConfig>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val tag = keys.next()
            val cfg = o.optJSONObject(tag) ?: continue
            out[tag] = LanguageConfig(
                espeakVoice = str(cfg, "espeakVoice"),
                lexicons = cfg.optJSONArray("lexicons")?.let { arr -> strings(arr).map(::safeName) },
                dictDir = str(cfg, "dictDir")?.let(::safeName),
                ruleFsts = cfg.optJSONArray("ruleFsts")?.let { arr -> strings(arr).map(::safeName) },
            )
        }
        return out
    }

    private fun required(o: JSONObject, key: String): String =
        str(o, key) ?: throw InvalidManifestException("missing '$key'")

    private fun str(o: JSONObject, key: String): String? {
        if (o.isNull(key)) return null
        val v = o.optString(key, "")
        return v.ifEmpty { null }
    }

    private fun strings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            if (arr.isNull(i)) continue
            val s = arr.optString(i, "")
            if (s.isNotEmpty()) out += s
        }
        return out
    }

    private fun stringMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            str(o, k)?.let { out[k] = it }
        }
        return out
    }
}
