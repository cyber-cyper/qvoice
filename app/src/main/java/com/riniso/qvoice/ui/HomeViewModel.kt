package com.riniso.qvoice.ui

import android.app.Application
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import com.riniso.qvoice.QVoiceApp
import com.riniso.qvoice.engine.SpeedEstimate
import com.riniso.qvoice.engine.ThreadPolicy
import com.riniso.qvoice.reader.ReadAloud
import com.riniso.qvoice.service.ExposedVoice
import com.riniso.qvoice.service.SampleTexts
import com.riniso.qvoice.service.TtsLocales
import com.riniso.qvoice.voices.Gender
import com.riniso.qvoice.voices.VoiceStore
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class EngineState { CONNECTING, READY, FAILED }

/**
 * What "Try it" shows after speaking. [speedFactor] and [threads] come from
 * the engine's own measurement (AppGraph.lastSpeech): how many times faster
 * than real time the voice generated, excluding waits for playback.
 */
data class Timing(
    val firstAudioMs: Long,
    val totalMs: Long?,
    val speedFactor: Float? = null,
    val threads: Int? = null,
)

/**
 * The CPU-threads setting under "Try it". [choice] 0 = automatic, which
 * means [auto] threads on this phone; [options] are the choices offered.
 */
data class ThreadsUi(val choice: Int, val auto: Int, val options: List<Int>)

data class VoiceRow(
    val voiceName: String,
    val displayName: String,
    val modelName: String,
    val gender: Gender,
    val languageTag: String,
    val language: String,
    val isDefault: Boolean,
    /** Speed of the voice's pack on this phone (measured or predicted); null if unknown. */
    val speed: SpeedEstimate? = null,
) {
    companion object {
        fun of(v: ExposedVoice, isDefault: Boolean, speed: SpeedEstimate?) = VoiceRow(
            voiceName = v.name,
            displayName = v.speaker?.displayName ?: v.voice.manifest.displayName,
            modelName = v.voice.manifest.displayName,
            gender = v.speaker?.gender ?: Gender.UNKNOWN,
            languageTag = v.languageTag,
            language = v.language,
            isDefault = isDefault,
            speed = speed,
        )

        /**
         * The reader's voice choice: every voice that speaks [language] (all
         * its regions: an English paragraph on an Indian phone can use any
         * English voice), with [current], the one reading it now, marked.
         */
        fun listFor(language: String, voices: List<ExposedVoice>, current: String?, speedOf: (String) -> SpeedEstimate?): List<VoiceRow> =
            voices.filter { it.language == language }.map { of(it, isDefault = it.name == current, speed = speedOf(it.voice.id)) }
    }
}

/** One language the installed voices speak, for the home screen's language chips. */
data class LanguageChip(val tag: String, val label: String, val voiceCount: Int)

data class HomeUiState(
    val engineState: EngineState = EngineState.CONNECTING,
    /** Null until known (the TTS connection reports it). */
    val isDefaultEngine: Boolean? = null,
    val languages: List<LanguageChip> = emptyList(),
    val selectedLanguage: String? = null,
    /** Voices of [selectedLanguage] only: with Kokoro and Supertonic installed there are hundreds. */
    val voices: List<VoiceRow> = emptyList(),
    val selectedVoice: String? = null,
    /** How many languages the voice library offers, for the "More voices" card. */
    val catalogueLanguages: Int = 0,
    /** Installed voice packs that aren't built in. */
    val downloadedVoices: Int = 0,
    val text: String = "",
    val rate: Float = 1f,
    val pitch: Float = 1f,
    val speaking: Boolean = false,
    val timing: Timing? = null,
    val errorCode: Int? = null,
    val threads: ThreadsUi = ThreadsUi(choice = 0, auto = 0, options = listOf(0)),
)

/**
 * State for the home screen. Voice rows come from the app's own voice list
 * (it knows display names and genders); speaking goes through [TtsClient] so
 * it uses the real engine service. Event callbacks arrive on binder and
 * background threads; MutableStateFlow.update is thread-safe.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app), TtsClient.Events {

    private val graph = (app as QVoiceApp).graph

    // Declared before `client` on purpose: TtsClient can report a failed
    // connection synchronously from its constructor, which calls onReady()
    // below — so everything the callbacks touch must already be initialised.
    private val _state = MutableStateFlow(HomeUiState(text = SampleTexts.forLanguage("en")))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** What the reader holds, for Home's Read aloud card (read on the main thread, like the reader). */
    val reading: StateFlow<ReadAloud.State> get() = graph.readAloud.state

    @Volatile private var currentUtterance: String? = null
    @Volatile private var speakRequestedAt = 0L
    @Volatile private var firstAudioMs = -1L

    private val client = TtsClient(app, this)

    private val storeListener = VoiceStore.Listener { refreshVoices() }

    init {
        graph.voiceStore.addListener(storeListener)
        refreshVoices()
        refreshThreads()
    }

    /** The user may have changed the preferred engine in Settings while away. */
    fun onResume() {
        refreshDefaultEngine()
        refreshVoices()
    }

    fun onTextChange(text: String) = _state.update { it.copy(text = text) }

    fun onRateChange(rate: Float) = _state.update { it.copy(rate = rate) }

    fun onPitchChange(pitch: Float) = _state.update { it.copy(pitch = pitch) }

    fun select(voiceName: String) = _state.update { it.copy(selectedVoice = voiceName) }

    fun selectLanguage(tag: String) {
        _state.update { s ->
            s.copy(selectedLanguage = tag, selectedVoice = null, text = sampleFor(s.text, s.selectedLanguage, tag))
        }
        refreshVoices()
    }

    /** Makes [row] the default for its language (QVoiceSettings.makeDefault). */
    fun makeDefault(row: VoiceRow) {
        graph.settings.makeDefault(row.voiceName, row.languageTag, row.language)
        refreshVoices()
    }

    fun speak() {
        val s = _state.value
        startSpeaking(s.text, s.selectedVoice, s.rate, s.pitch)
    }

    fun preview(row: VoiceRow) {
        select(row.voiceName)
        startSpeaking(SampleTexts.forLanguage(row.language), row.voiceName, _state.value.rate, _state.value.pitch)
    }

    fun stop() = client.stop()

    /**
     * 0 = automatic. Applies from the next sentence: each voice reloads with
     * the new count the next time it speaks (EngineHost compares the loader's
     * signature), so the first Speak afterwards includes a model load.
     */
    fun setThreads(choice: Int) {
        graph.settings.engineThreads = choice
        refreshThreads()
    }

    private fun refreshThreads() {
        val cores = Runtime.getRuntime().availableProcessors()
        _state.update {
            it.copy(threads = ThreadsUi(graph.settings.engineThreads, graph.autoThreads, threadOptions(cores)))
        }
    }

    private fun startSpeaking(text: String, voiceName: String?, rate: Float, pitch: Float) {
        if (text.isBlank()) return
        speakRequestedAt = SystemClock.elapsedRealtime()
        firstAudioMs = -1L
        val id = client.speak(text, voiceName, rate, pitch)
        currentUtterance = id
        _state.update {
            it.copy(speaking = id != null, timing = null, errorCode = if (id == null) TextToSpeech.ERROR else null)
        }
    }

    private fun refreshVoices() {
        val all = graph.exposedVoices
        val chips = languageChips(all)
        val catalogueLanguages = graph.catalog.entries
            .flatMap { e -> e.manifest.languages.map { TtsLocales.parseTag(it)?.first ?: it } }
            .toSet().size
        val downloaded = graph.voiceStore.voices().count { !it.bundled }
        _state.update { s ->
            val language = s.selectedLanguage?.takeIf { tag -> chips.any { it.tag == tag } }
                ?: preferredLanguage(chips, Locale.getDefault()) { lang, region -> graph.selector.defaultFor(lang, region)?.languageTag }
            val rows = all.filter { it.languageTag == language }.map { v -> rowFor(v) }
            val keep = s.selectedVoice?.takeIf { selected -> rows.any { it.voiceName == selected } }
            s.copy(
                languages = chips,
                selectedLanguage = language,
                voices = rows,
                selectedVoice = keep ?: rows.firstOrNull { it.isDefault }?.voiceName ?: rows.firstOrNull()?.voiceName,
                catalogueLanguages = catalogueLanguages,
                downloadedVoices = downloaded,
                text = sampleFor(s.text, s.selectedLanguage, language),
            )
        }
    }

    private fun rowFor(v: ExposedVoice): VoiceRow {
        // Default "for this language and region" is what apps asking for it get.
        val region = TtsLocales.parseTag(v.languageTag)?.second
        return VoiceRow.of(
            v,
            isDefault = graph.selector.defaultFor(v.language, region)?.name == v.name,
            speed = graph.speedEstimate(v.voice.id),
        )
    }

    private fun refreshDefaultEngine() {
        if (!client.isReady) return
        val engine = client.defaultEngine()
        _state.update { it.copy(isDefaultEngine = engine == getApplication<Application>().packageName) }
    }

    override fun onReady(success: Boolean) {
        _state.update { it.copy(engineState = if (success) EngineState.READY else EngineState.FAILED) }
        if (success) refreshDefaultEngine()
    }

    override fun onStart(utteranceId: String) {
        if (utteranceId != currentUtterance) return
        firstAudioMs = SystemClock.elapsedRealtime() - speakRequestedAt
        _state.update { it.copy(timing = Timing(firstAudioMs, null)) }
    }

    override fun onDone(utteranceId: String) {
        if (utteranceId != currentUtterance) return
        val total = SystemClock.elapsedRealtime() - speakRequestedAt
        // The engine records each utterance before it finishes playing, so
        // by onDone this is ours — unless another app spoke in between,
        // which the caller check rules out.
        val stats = graph.lastSpeech?.takeIf { it.caller == getApplication<Application>().packageName }
        val timing = Timing(
            firstAudioMs = firstAudioMs.coerceAtLeast(0),
            totalMs = total,
            speedFactor = stats?.speedFactor?.takeIf { it > 0f },
            threads = stats?.threads?.takeIf { it > 0 },
        )
        _state.update { it.copy(speaking = false, timing = timing) }
        // The utterance just measured this voice's speed (and may have
        // changed which voice keeps up): show it in the list.
        refreshVoices()
    }

    override fun onError(utteranceId: String, errorCode: Int) {
        if (utteranceId != currentUtterance) return
        _state.update { it.copy(speaking = false, errorCode = errorCode) }
    }

    override fun onStopped(utteranceId: String) {
        if (utteranceId != currentUtterance) return
        _state.update { it.copy(speaking = false) }
    }

    companion object {
        /**
         * Thread counts offered: automatic (0), then 1-4, then 6 and 8 where
         * the phone has that many cores. More than 8 gains nothing on a phone.
         */
        internal fun threadOptions(cores: Int): List<Int> =
            listOf(0) + listOf(1, 2, 3, 4, 6, 8).filter { it <= maxOf(1, cores) && it <= ThreadPolicy.MAX_THREADS }

        /**
         * The "Try it" text after switching from language [from] to [to]: that
         * language's sample, unless the user has typed their own text. (The
         * first selection counts as a switch from English, the initial text.)
         */
        internal fun sampleFor(text: String, from: String?, to: String?): String {
            fun sample(tag: String?) = SampleTexts.forLanguage(tag?.let { TtsLocales.parseTag(it)?.first } ?: "en")
            return if (text.isBlank() || text == sample(from)) sample(to) else text
        }

        /** One chip per language tag the installed voices speak, named in the phone's language. */
        internal fun languageChips(voices: List<ExposedVoice>): List<LanguageChip> =
            voices.groupBy { it.languageTag }
                .map { (tag, list) -> LanguageChip(tag, TtsLocales.localeFor(tag).displayName, list.size) }
                .sortedBy { it.label.lowercase(Locale.ROOT) }

        /**
         * The language shown first: the phone's own language and region if a
         * voice speaks it; else the group holding the voice apps would get for
         * the phone's language ([defaultTag], from VoiceSelector) — an en-IN
         * phone gets whichever English voice is the default, not the first
         * English group alphabetically; else English's; else the first.
         */
        internal fun preferredLanguage(
            chips: List<LanguageChip>,
            locale: Locale,
            defaultTag: (language: String, region: String?) -> String?,
        ): String? {
            if (chips.isEmpty()) return null
            val region = locale.country.ifEmpty { null }
            val own = TtsLocales.tagOf(locale.language, region)
            chips.firstOrNull { it.tag.equals(own, ignoreCase = true) }?.let { return it.tag }
            for ((language, r) in listOf(locale.language to region, "en" to null)) {
                val tag = defaultTag(language, r) ?: continue
                chips.firstOrNull { it.tag == tag }?.let { return it.tag }
            }
            return chips.first().tag
        }
    }

    override fun onCleared() {
        graph.voiceStore.removeListener(storeListener)
        client.shutdown()
    }
}
