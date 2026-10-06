package com.riniso.qvoice.ui

import android.app.Application
import android.net.ConnectivityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riniso.qvoice.QVoiceApp
import com.riniso.qvoice.catalog.CatalogEntry
import com.riniso.qvoice.catalog.Licences
import com.riniso.qvoice.download.PackItem
import com.riniso.qvoice.engine.SpeedEstimate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A question or notice the voice library screen is showing. */
sealed interface VoicesDialog {
    /** The voice is expected (or known) to run slower than real time on this phone. */
    data class SlowVoice(val entry: CatalogEntry, val speed: SpeedEstimate) : VoicesDialog
    data class AcceptLicence(val entry: CatalogEntry, val licenceText: String) : VoicesDialog
    data class NoSpace(val entry: CatalogEntry, val needed: Long, val free: Long) : VoicesDialog
    data class Metered(val entry: CatalogEntry) : VoicesDialog
    data class ConfirmDelete(val item: PackItem) : VoicesDialog
    data class Licence(val name: String, val text: String) : VoicesDialog
}

/**
 * The voice library screen. State comes from [com.riniso.qvoice.download.VoiceLibrary];
 * this adds each voice's speed on this phone ([speeds]) and the questions
 * asked before a download starts, in this order: too slow for this phone
 * (first: no point accepting a licence for a voice you then skip), licence
 * acceptance (OpenRAIL-M), free space, mobile data for big downloads.
 */
class VoicesViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as QVoiceApp).graph
    private val library = graph.library

    val items: StateFlow<List<PackItem>> = library.items

    private val _dialog = MutableStateFlow<VoicesDialog?>(null)
    val dialog: StateFlow<VoicesDialog?> = _dialog.asStateFlow()

    private val _freeSpace = MutableStateFlow(0L)
    val freeSpace: StateFlow<Long> = _freeSpace.asStateFlow()

    val catalogueMissing: Boolean get() = graph.catalog.entries.isEmpty()

    /** Speed on this phone per pack id (measured or predicted); packs with no estimate are absent. */
    private val _speeds = MutableStateFlow<Map<String, SpeedEstimate>>(emptyMap())
    val speeds: StateFlow<Map<String, SpeedEstimate>> = _speeds.asStateFlow()

    private var poller: Job? = null

    init {
        library.publish()
        // Predictions need the built-in voice's speed; measure it now if no
        // utterance has yet (the polling below then shows the result).
        graph.checkSpeedIfNeeded()
        refreshSpeeds()
    }

    /**
     * DownloadManager has no progress callback, so the screen polls it once a
     * second while visible (and only then).
     */
    fun startPolling() {
        if (poller?.isActive == true) return
        poller = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                library.refresh()
                _freeSpace.value = library.freeSpace()
                refreshSpeeds()
                delay(POLL_MILLIS)
            }
        }
    }

    fun stopPolling() {
        poller?.cancel()
        poller = null
    }

    /**
     * Download, retry and update all start here. An update isn't questioned
     * about speed: the voice is already on the phone.
     */
    fun onDownload(item: PackItem) {
        item.entry?.let { askThenDownload(it, speedChecked = item.installed != null) }
    }

    fun onSlowVoiceChoice(entry: CatalogEntry, downloadAnyway: Boolean) {
        _dialog.value = null
        if (downloadAnyway) askThenDownload(entry, speedChecked = true)
    }

    fun onAcceptLicence(entry: CatalogEntry) {
        entry.acceptance?.let { graph.settings.accept(it) }
        _dialog.value = null
        askThenDownload(entry, speedChecked = true)
    }

    fun onMeteredChoice(entry: CatalogEntry, downloadNow: Boolean) {
        _dialog.value = null
        start(entry, allowMetered = downloadNow)
    }

    fun onCancel(item: PackItem) = io { library.cancel(item.id) }

    fun onDelete(item: PackItem) {
        _dialog.value = VoicesDialog.ConfirmDelete(item)
    }

    fun onDeleteConfirmed(item: PackItem) {
        _dialog.value = null
        io { library.delete(item.id) }
    }

    fun onDismissProblem(item: PackItem) = io { library.dismissProblem(item.id) }

    fun onShowLicence(item: PackItem) {
        val name = item.manifest.license.name
        val text = Licences.textAssetFor(name)?.let { readAsset(it) } ?: item.manifest.license.url
        _dialog.value = VoicesDialog.Licence(name, text)
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    private fun askThenDownload(entry: CatalogEntry, speedChecked: Boolean) {
        val speed = graph.speedEstimate(entry.id)
        val acceptance = entry.acceptance
        val big = entry.download.size > BIG_DOWNLOAD_BYTES
        val question = nextQuestion(
            slow = !speedChecked && speed != null && !speed.keepsUp,
            licenceToAccept = acceptance != null && !graph.settings.hasAccepted(acceptance),
            enoughSpace = { library.hasSpaceFor(entry) },
            bigOnMobileData = { big && isMetered() },
        )
        _dialog.value = when (question) {
            Question.SLOW -> VoicesDialog.SlowVoice(entry, speed!!)
            Question.LICENCE -> VoicesDialog.AcceptLicence(
                entry,
                Licences.TEXT_ASSETS[acceptance]?.let { readAsset(it) }.orEmpty(),
            )
            Question.SPACE -> VoicesDialog.NoSpace(entry, library.bytesNeeded(entry), library.freeSpace())
            Question.MOBILE_DATA -> VoicesDialog.Metered(entry)
            Question.NONE -> {
                // On Wi-Fi a big download still waits for Wi-Fi if the phone
                // drops to mobile data halfway; small ones just carry on.
                start(entry, allowMetered = !big)
                null
            }
        }
    }

    private fun start(entry: CatalogEntry, allowMetered: Boolean) = io { library.download(entry.id, allowMetered) }

    private fun refreshSpeeds() {
        _speeds.value = library.items.value.mapNotNull { item -> graph.speedEstimate(item.id)?.let { item.id to it } }.toMap()
    }

    private fun isMetered(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return true
        return cm.isActiveNetworkMetered
    }

    private fun readAsset(path: String): String =
        runCatching { getApplication<Application>().assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("")

    private fun io(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    override fun onCleared() {
        stopPolling()
    }

    /** What must be asked before a download can start. */
    enum class Question { SLOW, LICENCE, SPACE, MOBILE_DATA, NONE }

    companion object {
        private const val POLL_MILLIS = 1_000L

        /**
         * The first question still to ask, in this order: too slow for this
         * phone (first: no point accepting a licence, or making room, for a
         * voice you then skip), licence acceptance, free space, a big
         * download on mobile data. Each "yes" re-runs this with that question
         * answered. Space and network are only looked at when reached (they
         * cost a call to the system).
         */
        internal fun nextQuestion(
            slow: Boolean,
            licenceToAccept: Boolean,
            enoughSpace: () -> Boolean,
            bigOnMobileData: () -> Boolean,
        ): Question = when {
            slow -> Question.SLOW
            licenceToAccept -> Question.LICENCE
            !enoughSpace() -> Question.SPACE
            bigOnMobileData() -> Question.MOBILE_DATA
            else -> Question.NONE
        }

        /** Above this, ask before using mobile data (every catalogue voice is bigger; the setting is for future small ones). */
        const val BIG_DOWNLOAD_BYTES = 50L * 1024 * 1024
    }
}
