package com.riniso.qvoice.ui

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riniso.qvoice.R
import com.riniso.qvoice.download.DownloadPhase
import com.riniso.qvoice.download.PackItem
import com.riniso.qvoice.download.PackState
import com.riniso.qvoice.download.Problem
import com.riniso.qvoice.engine.SpeedEstimate
import com.riniso.qvoice.engine.SpeedTier
import com.riniso.qvoice.service.SpeechStats
import com.riniso.qvoice.service.TtsLocales
import com.riniso.qvoice.voices.ArchiveInstaller
import com.riniso.qvoice.voices.VoiceManifest

/**
 * The voice library: voices on the phone (with Delete), voices to download
 * (with size, languages and licence), progress while downloading and
 * installing, and the questions asked before a download.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicesScreen(onBack: () -> Unit, vm: VoicesViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    val free by vm.freeSpace.collectAsStateWithLifecycle()
    val speeds by vm.speeds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Poll DownloadManager only while this screen is visible.
    LifecycleResumeEffect(Unit) {
        vm.startPolling()
        onPauseOrDispose { vm.stopPolling() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val installed = items.filter { it.installed != null }
        val available = items.filter { it.installed == null }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = readablePadding(padding, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (free > 0) {
                    item {
                        Text(
                            stringResource(R.string.library_free_space, size(context, free)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (vm.catalogueMissing) {
                    item { Text(stringResource(R.string.library_empty_catalogue), color = MaterialTheme.colorScheme.error) }
                }
                if (installed.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.library_installed_title)) }
                    items(installed, key = { it.id }) { item -> PackCard(item, speeds[item.id], vm) }
                }
                if (available.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.library_available_title)) }
                    items(available, key = { it.id }) { item -> PackCard(item, speeds[item.id], vm) }
                }
            }
        }
    }

    dialog?.let { VoicesDialogs(it, vm) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun PackCard(item: PackItem, speed: SpeedEstimate?, vm: VoicesViewModel) {
    val context = LocalContext.current
    val m = item.manifest
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(m.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val voices = m.speakers.size.coerceAtLeast(1)
            Text(
                pluralStringResource(R.plurals.pack_voice_count, voices, voices) + " · " + languageSummary(m),
                style = MaterialTheme.typography.bodyMedium,
            )
            item.entry?.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            val sizes = when {
                item.installed != null && item.entry != null && item.state !is PackState.Installed ->
                    stringResource(R.string.pack_sizes_download, size(context, item.entry.download.size), size(context, item.entry.installedSize))
                item.installed != null -> item.entry?.let { stringResource(R.string.pack_size_installed, size(context, it.installedSize)) }
                item.entry != null ->
                    stringResource(R.string.pack_sizes_download, size(context, item.entry.download.size), size(context, item.entry.installedSize))
                else -> null
            }
            sizes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            speed?.let { SpeedLine(it) }
            PackStateRow(item, vm)
            TextButton(onClick = { vm.onShowLicence(item) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.action_licence, m.license.name))
            }
        }
    }
}

/**
 * "Fast on this phone · 2.8× real time" — measured once the voice has spoken
 * here, "Expected to…" before (predicted from the built-in voice's speed).
 */
@Composable
private fun SpeedLine(speed: SpeedEstimate) {
    val factor = SpeechStats.formatFactor(speed.factor)
    val (text, color) = when (speed.tier) {
        SpeedTier.FAST -> stringResource(
            if (speed.measured) R.string.speed_fast_measured else R.string.speed_fast_expected,
            factor,
        ) to MaterialTheme.colorScheme.primary
        SpeedTier.KEEPS_UP -> stringResource(
            if (speed.measured) R.string.speed_keeps_up_measured else R.string.speed_keeps_up_expected,
            factor,
        ) to MaterialTheme.colorScheme.onSurfaceVariant
        SpeedTier.SLOW -> stringResource(
            if (speed.measured) R.string.speed_slow_measured else R.string.speed_slow_expected,
            factor,
        ) to MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun PackStateRow(item: PackItem, vm: VoicesViewModel) {
    val context = LocalContext.current
    when (val state = item.state) {
        PackState.Available -> Button(onClick = { vm.onDownload(item) }) { Text(stringResource(R.string.action_download)) }

        is PackState.Downloading -> {
            if (state.phase == DownloadPhase.RUNNING && state.total > 0) {
                LinearProgressIndicator(
                    progress = { (state.downloaded.toDouble() / state.total).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val label = when (state.phase) {
                DownloadPhase.QUEUED -> stringResource(R.string.state_queued)
                DownloadPhase.WAITING_FOR_NETWORK -> stringResource(R.string.state_waiting_network)
                DownloadPhase.WAITING_FOR_WIFI -> stringResource(R.string.state_waiting_wifi)
                DownloadPhase.RETRYING -> stringResource(R.string.state_retrying)
                else -> stringResource(R.string.state_downloading, size(context, state.downloaded), size(context, state.total))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.onCancel(item) }) { Text(stringResource(R.string.action_cancel)) }
            }
        }

        is PackState.Installing -> {
            // Can't be cancelled: it is short, and stopping mid-way gains nothing.
            LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                if (state.phase == ArchiveInstaller.Phase.VERIFYING) {
                    stringResource(R.string.state_verifying)
                } else {
                    stringResource(R.string.state_installing, (state.fraction * 100).toInt())
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        is PackState.Installed -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    when {
                        item.bundled -> R.string.pack_built_in
                        state.updateAvailable -> R.string.pack_update_available
                        else -> R.string.pack_installed
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (state.updateAvailable) {
                Button(onClick = { vm.onDownload(item) }) { Text(stringResource(R.string.action_update)) }
            }
            if (!item.bundled) {
                OutlinedButton(onClick = { vm.onDelete(item) }) { Text(stringResource(R.string.action_delete)) }
            }
        }

        is PackState.Failed -> {
            Text(problemText(state.problem), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.entry != null) {
                    Button(onClick = { vm.onDownload(item) }) { Text(stringResource(R.string.action_retry)) }
                }
                TextButton(onClick = { vm.onDismissProblem(item) }) { Text(stringResource(R.string.action_dismiss)) }
            }
        }

        PackState.Deleting -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.state_deleting), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VoicesDialogs(dialog: VoicesDialog, vm: VoicesViewModel) {
    val context = LocalContext.current
    when (dialog) {
        is VoicesDialog.SlowVoice -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text(stringResource(R.string.dialog_slow_title)) },
            text = {
                Text(
                    stringResource(
                        if (dialog.speed.measured) R.string.dialog_slow_body_measured else R.string.dialog_slow_body_expected,
                        dialog.entry.manifest.displayName,
                        SpeechStats.formatFactor(dialog.speed.factor),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.onSlowVoiceChoice(dialog.entry, downloadAnyway = true) }) {
                    Text(stringResource(R.string.action_download_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.onSlowVoiceChoice(dialog.entry, downloadAnyway = false) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

        is VoicesDialog.AcceptLicence -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text(stringResource(R.string.dialog_accept_title, dialog.entry.manifest.displayName)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.dialog_accept_body))
                    Spacer(Modifier.height(12.dp))
                    Text(dialog.licenceText, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.onAcceptLicence(dialog.entry) }) { Text(stringResource(R.string.action_accept_and_download)) }
            },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text(stringResource(R.string.action_cancel)) } },
        )

        is VoicesDialog.Metered -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text(stringResource(R.string.dialog_metered_title)) },
            text = {
                Text(stringResource(R.string.dialog_metered_body, dialog.entry.manifest.displayName, size(context, dialog.entry.download.size)))
            },
            confirmButton = {
                TextButton(onClick = { vm.onMeteredChoice(dialog.entry, downloadNow = true) }) { Text(stringResource(R.string.action_download_now)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.onMeteredChoice(dialog.entry, downloadNow = false) }) { Text(stringResource(R.string.action_wait_for_wifi)) }
            },
        )

        is VoicesDialog.NoSpace -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text(stringResource(R.string.dialog_space_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_space_body,
                        dialog.entry.manifest.displayName,
                        size(context, dialog.needed),
                        size(context, dialog.free),
                    ),
                )
            },
            confirmButton = { TextButton(onClick = vm::dismissDialog) { Text(stringResource(R.string.action_close)) } },
        )

        is VoicesDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text(stringResource(R.string.dialog_delete_title, dialog.item.manifest.displayName)) },
            text = { Text(stringResource(R.string.dialog_delete_body)) },
            confirmButton = {
                TextButton(onClick = { vm.onDeleteConfirmed(dialog.item) }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text(stringResource(R.string.action_cancel)) } },
        )

        is VoicesDialog.Licence -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text(dialog.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(dialog.text, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissDialog) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun problemText(problem: Problem): String = stringResource(
    when (problem) {
        Problem.NO_SPACE -> R.string.problem_no_space
        Problem.NETWORK -> R.string.problem_network
        Problem.GONE -> R.string.problem_gone
        Problem.NO_STORAGE -> R.string.problem_no_storage
        Problem.CHECKSUM -> R.string.problem_checksum
        Problem.INSTALL -> R.string.problem_install
    },
)

/** "English, Hindi, Spanish" — or the first five "and 26 more" for Supertonic. */
@Composable
private fun languageSummary(m: VoiceManifest): String {
    val names = m.languages
        .map { TtsLocales.parseTag(it)?.first ?: it }
        .distinct()
        .map { TtsLocales.localeFor(it).displayLanguage }
    return if (names.size <= 6) {
        names.joinToString(", ")
    } else {
        stringResource(R.string.pack_languages_more, names.take(5).joinToString(", "), names.size - 5)
    }
}

private fun size(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)
