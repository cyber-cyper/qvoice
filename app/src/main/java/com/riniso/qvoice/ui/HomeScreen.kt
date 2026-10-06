package com.riniso.qvoice.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riniso.qvoice.R
import com.riniso.qvoice.reader.ReadAloud
import com.riniso.qvoice.reader.ReaderText
import com.riniso.qvoice.service.SpeechStats
import com.riniso.qvoice.service.TtsLocales
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenAbout: () -> Unit, onOpenVoices: () -> Unit, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val reading by vm.reading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Decorative: the app name next to it says the same.
                        Image(
                            painter = painterResource(R.drawable.qvoice_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    TextButton(onClick = onOpenVoices) { Text(stringResource(R.string.library_title)) }
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.action_about))
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyColumn(
                // The Scaffold's padding goes into contentPadding below, so it is
                // marked consumed here: imePadding then adds only what the keyboard
                // covers beyond the navigation bar, instead of counting the bar twice.
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding).imePadding(),
                contentPadding = readablePadding(padding, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SetupCard(state, onOpenSettings = { openTtsSettings(context) }) }
                item {
                    TryItCard(
                        state = state,
                        onTextChange = vm::onTextChange,
                        onRateChange = vm::onRateChange,
                        onPitchChange = vm::onPitchChange,
                        onSpeak = vm::speak,
                        onStop = vm::stop,
                        onChangeThreads = vm::setThreads,
                    )
                }
                item {
                    ReadAloudCard(
                        reading,
                        onOpen = { context.startActivity(Intent(context, ReaderActivity::class.java)) },
                        onContinue = {
                            context.startActivity(Intent(context, ReaderActivity::class.java).setAction(ReaderActivity.ACTION_CONTINUE))
                        },
                    )
                }
                // Until something is downloaded, the library is the next step: show it first.
                if (state.downloadedVoices == 0) item { MoreVoicesCard(state.catalogueLanguages, onOpenVoices) }
                if (state.languages.size > 1) {
                    item {
                        Text(
                            stringResource(R.string.home_language_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.languages, key = { it.tag }) { chip ->
                                FilterChip(
                                    selected = chip.tag == state.selectedLanguage,
                                    onClick = { vm.selectLanguage(chip.tag) },
                                    label = { Text("${chip.label} · ${chip.voiceCount}") },
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.voices_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
                items(state.voices, key = { it.voiceName }) { row ->
                    VoiceRowItem(
                        row = row,
                        selected = row.voiceName == state.selectedVoice,
                        onSelect = { vm.select(row.voiceName) },
                        onPreview = { vm.preview(row) },
                        onMakeDefault = { vm.makeDefault(row) },
                    )
                }
                if (state.downloadedVoices > 0) item { MoreVoicesCard(state.catalogueLanguages, onOpenVoices) }
            }
        }
    }
}

/**
 * The one thing that matters on first run: making QVoice the phone's
 * text-to-speech engine. Until it is, a card in the logo's colours with the
 * step and the button (the whole first-run guide — no carousel to swipe
 * through); afterwards a small confirmation.
 */
@Composable
private fun SetupCard(state: HomeUiState, onOpenSettings: () -> Unit) {
    when {
        state.engineState == EngineState.CONNECTING -> NoticeCard { Text(stringResource(R.string.status_connecting)) }
        state.engineState == EngineState.FAILED -> NoticeCard {
            Text(stringResource(R.string.status_engine_failed), color = MaterialTheme.colorScheme.error)
        }
        state.isDefaultEngine == true -> DefaultEngineCard(onOpenSettings)
        else -> MakeDefaultCard(onOpenSettings)
    }
}

@Composable
private fun NoticeCard(content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/**
 * Always the dark brand card (like the icon), in light and dark theme alike;
 * its text colours are fixed to match (all at least 4.5:1 on the gradient).
 */
@Composable
private fun MakeDefaultCard(onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF14104F), Color(0xFF2D2398), Color(0xFF5B2A9E))))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.qvoice_logo),
                contentDescription = null, // the title next to it carries the meaning
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.semantics { heading() })
        }
        Text(stringResource(R.string.setup_body), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE3DFFF))
        Text(stringResource(R.string.setup_steps), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9F1FC))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF231A8C)),
        ) {
            Text(stringResource(R.string.action_open_tts_settings))
        }
        Text(stringResource(R.string.setup_note), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB9B2FF))
    }
}

@Composable
private fun DefaultEngineCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.status_is_default_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.status_is_default_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        TextButton(onClick = onOpenSettings, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.action_tts_settings_short))
        }
    }
}

@Composable
private fun MoreVoicesCard(languages: Int, onOpenVoices: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_more_voices_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (languages > 0) Text(stringResource(R.string.home_more_voices_body, languages))
            Button(onClick = onOpenVoices) { Text(stringResource(R.string.action_get_more_voices)) }
        }
    }
}

/**
 * Points at the reader, and at "Read aloud" in other apps' text selections,
 * which people rarely discover alone. With a text in the reader (kept across
 * restarts, ReaderMemory), one tap goes on listening from where it stopped.
 */
@Composable
private fun ReadAloudCard(reading: ReadAloud.State, onOpen: () -> Unit, onContinue: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_read_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (reading.paragraphs.isEmpty()) {
                Text(stringResource(R.string.home_read_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.action_open_reader)) }
            } else {
                // The text in the reader: its opening words and where it is, to go straight back.
                Text(
                    stringResource(R.string.home_read_quote, ReaderText.snippet(reading.paragraphs.first())),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val paragraph = reading.index + 1
                val paragraphs = reading.paragraphs.size
                Text(
                    when (reading.status) {
                        ReadAloud.Status.PLAYING -> stringResource(R.string.home_read_playing, paragraph, paragraphs)
                        ReadAloud.Status.PAUSED -> stringResource(R.string.home_read_paused, paragraph, paragraphs)
                        ReadAloud.Status.FINISHED -> stringResource(R.string.reader_finished)
                        ReadAloud.Status.READY, ReadAloud.Status.EMPTY -> stringResource(R.string.home_read_ready, paragraph, paragraphs)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Reading: back to it. Otherwise: open it reading, from its place.
                when (reading.status) {
                    ReadAloud.Status.PLAYING ->
                        Button(onClick = onOpen) { Text(stringResource(R.string.action_back_to_reader)) }
                    ReadAloud.Status.FINISHED ->
                        Button(onClick = onContinue) { Text(stringResource(R.string.action_listen_again)) }
                    ReadAloud.Status.PAUSED, ReadAloud.Status.READY, ReadAloud.Status.EMPTY ->
                        Button(onClick = onContinue) {
                            Text(
                                stringResource(
                                    if (reading.index > 0 || reading.status == ReadAloud.Status.PAUSED) {
                                        R.string.action_continue_listening
                                    } else {
                                        R.string.action_start_listening
                                    },
                                ),
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun TryItCard(
    state: HomeUiState,
    onTextChange: (String) -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onSpeak: () -> Unit,
    onStop: () -> Unit,
    onChangeThreads: (Int) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.try_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                label = { Text(stringResource(R.string.try_text_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            // Up to 3x in quarter steps (9 stops between the ends): past 1.5x
            // the engine time-stretches, which the slider lets people hear.
            // TalkBack reads the value as "Speed 1.25x", not a percentage.
            val speedLabel = stringResource(R.string.try_speed, formatRate(state.rate))
            Text(speedLabel, style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = state.rate,
                onValueChange = onRateChange,
                modifier = Modifier.semantics { stateDescription = speedLabel },
                valueRange = 0.5f..3.0f,
                steps = 9,
            )
            // 0.5x to 1.5x in tenths: beyond that voices sound like cartoons.
            val pitchLabel = stringResource(R.string.try_pitch, formatRate(state.pitch))
            Text(pitchLabel, style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = state.pitch,
                onValueChange = onPitchChange,
                modifier = Modifier.semantics { stateDescription = pitchLabel },
                valueRange = 0.5f..1.5f,
                steps = 9,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSpeak,
                    enabled = state.engineState == EngineState.READY && state.text.isNotBlank(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_speak))
                }
                OutlinedButton(onClick = onStop, enabled = state.speaking) {
                    Text(stringResource(R.string.action_stop))
                }
            }
            state.timing?.let { t ->
                val label = if (t.totalMs == null) {
                    stringResource(R.string.timing_started, t.firstAudioMs)
                } else {
                    stringResource(R.string.timing_done, t.firstAudioMs, t.totalMs)
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val factor = t.speedFactor
                val threads = t.threads
                if (factor != null && threads != null) {
                    val speed = if (factor >= 1f) {
                        stringResource(R.string.timing_speed_fast, SpeechStats.formatFactor(factor), threads)
                    } else {
                        stringResource(R.string.timing_speed_slow, SpeechStats.formatFactor(factor), threads)
                    }
                    Text(speed, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.errorCode?.let { code ->
                Text(stringResource(R.string.error_speak, code), color = MaterialTheme.colorScheme.error)
            }
            ThreadsSetting(state.threads, onChangeThreads)
        }
    }
}

/**
 * "CPU threads: Automatic (4)" and the dialog to change it. Kept next to
 * "Try it" on purpose: the speed line above it shows the effect of a change
 * (the Speak right after a change includes reloading the voice).
 */
@Composable
private fun ThreadsSetting(threads: ThreadsUi, onChange: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val autoLabel = stringResource(R.string.threads_auto, threads.auto)
    val current = if (threads.choice == 0) autoLabel else threads.choice.toString()
    TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Text(stringResource(R.string.threads_label, current))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_close)) }
            },
            title = { Text(stringResource(R.string.threads_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.threads_dialog_body, threads.auto), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    for (option in threads.options) {
                        val selected = option == threads.choice
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onChange(option)
                                        open = false
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Decorative: the row is the click target and carries the role.
                            RadioButton(selected = selected, onClick = null)
                            Text(
                                if (option == 0) autoLabel else option.toString(),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

/**
 * One voice: initial in a gender-coloured circle, name with the Default
 * badge, gender · language · pack, its speed on this phone, and ▶ to hear
 * it. The card is the selection (for "Try it"); "Make default" appears on the
 * selected card only, which keeps the list calm.
 */
@Composable
private fun VoiceRowItem(
    row: VoiceRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    onMakeDefault: () -> Unit,
) {
    val gender = genderName(row.gender)
    // Language name in the phone's own language, e.g. "English (United States)".
    val languageName = TtsLocales.localeFor(row.languageTag).displayName
    val selectedLabel = stringResource(R.string.voice_selected)
    Card(
        onClick = onSelect,
        // No radio button: the card's colour shows the selection, and this tells TalkBack.
        modifier = Modifier.fillMaxWidth().semantics { if (selected) stateDescription = selectedLabel },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceAvatar(row)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.displayName, style = MaterialTheme.typography.titleSmall)
                    if (row.isDefault) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.voice_default_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    stringResource(R.string.voice_subtitle, gender, languageName, row.modelName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.speed?.let { SpeedLabel(it) }
                if (selected && !row.isDefault) {
                    TextButton(onClick = onMakeDefault, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(stringResource(R.string.action_make_default))
                    }
                }
            }
            IconButton(onClick = onPreview) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_preview, row.displayName))
            }
        }
    }
}

/** 1.0 -> "1", 1.25 -> "1.25", 0.5 -> "0.5". */
private fun formatRate(rate: Float): String =
    String.format(Locale.US, "%.2f", rate).trimEnd('0').trimEnd('.')

/**
 * Android has no public constant for the text-to-speech settings page; the
 * action below is what the Settings app registers on AOSP, Pixel, Samsung and
 * most OEM builds. Falls back to Accessibility, then to Settings itself.
 */
private fun openTtsSettings(context: Context) {
    val candidates = listOf(
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // try the next one
        }
    }
}
