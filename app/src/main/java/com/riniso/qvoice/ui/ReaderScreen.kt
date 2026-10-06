package com.riniso.qvoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riniso.qvoice.R
import com.riniso.qvoice.reader.ReadAloud
import com.riniso.qvoice.reader.ReaderText
import com.riniso.qvoice.reader.formatSleepTime
import java.text.NumberFormat
import java.util.Locale

/**
 * The reader: the text in paragraphs, the one being read highlighted and
 * kept in view, and play/pause, previous/next paragraph and speed at the
 * bottom. With no text yet, a field to paste or type into.
 *
 * All state lives in [reader] (app-wide); this only shows and drives it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    reader: ReadAloud,
    /** Why a share wasn't read, if one wasn't: above the text, or in the paste field when there is none. */
    notice: ShareNotice?,
    onDismissNotice: () -> Unit,
    /** Reads a clip marked sensitive after all (the notice's "Read it anyway"). */
    onReadClipboardAnyway: () -> Unit,
    onClose: () -> Unit,
    onOpenVoices: () -> Unit,
    /** The voices for a language, the one reading it marked (VoiceRow.listFor). */
    voicesFor: (Locale) -> List<VoiceRow>,
    /** Makes the voice its language's default and restarts the paragraph with it. */
    onChooseVoice: (VoiceRow) -> Unit,
) {
    val state by reader.state.collectAsStateWithLifecycle()
    // "Read something else" forgets the text (ReaderMemory deletes it); until
    // something new is read, one tap in the paste field brings it back.
    var cleared by remember { mutableStateOf<ReadAloud.State?>(null) }
    // No keep-screen-on: the phone's own screen timeout applies, and reading
    // goes on with the screen off (ReadAloudService).
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reader_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.paragraphs.isNotEmpty()) {
                        VoiceButton(reader.localeOf(state.index), voicesFor, onChooseVoice, onOpenVoices)
                        SleepTimerButton(state.sleepAt, onSet = reader::setSleepTimer)
                        // Back to the paste field, for something else to read
                        // (which also forgets the text: ReaderMemory).
                        IconButton(
                            onClick = {
                                onDismissNotice()
                                cleared = state
                                reader.load("", play = false)
                            },
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.reader_new_text))
                        }
                    }
                },
            )
        },
        bottomBar = { if (state.paragraphs.isNotEmpty()) ReaderControls(state, reader) },
    ) { padding ->
        if (state.paragraphs.isEmpty()) {
            PasteBox(
                notice,
                padding,
                onRead = { text ->
                    onDismissNotice()
                    cleared = null
                    reader.load(text, play = true)
                },
                onReadClipboardAnyway = onReadClipboardAnyway,
                onBackToPrevious = cleared?.let { previous ->
                    {
                        reader.restore(previous.paragraphs, previous.index, previous.truncated)
                        cleared = null
                    }
                },
            )
        } else {
            Paragraphs(state, notice, onDismissNotice, onReadClipboardAnyway, padding, reader, onOpenVoices)
        }
    }
}

@Composable
private fun Paragraphs(
    state: ReadAloud.State,
    notice: ShareNotice?,
    onDismissNotice: () -> Unit,
    onReadClipboardAnyway: () -> Unit,
    padding: PaddingValues,
    reader: ReadAloud,
    onOpenVoices: () -> Unit,
) {
    val list = rememberLazyListState()
    val playing = state.status == ReadAloud.Status.PLAYING
    // A text shown afresh (opened, recreated, or back from memory after a
    // restart) opens at its place, not at its top. The notices stay in view
    // when they matter more: at the start, or when reading stopped on a
    // problem whose card holds the buttons.
    LaunchedEffect(state.paragraphs) {
        list.scrollToItem(if (state.index == 0 || state.problem != null) 0 else state.index + NOTICE_ROWS)
    }
    // Follow the reading, but only while reading: paused, the list is the user's to scroll.
    LaunchedEffect(state.index, playing) {
        if (playing) list.animateScrollToItem(state.index + NOTICE_ROWS)
    }
    // A share that couldn't be read: its notice is at the top.
    LaunchedEffect(notice) {
        if (notice != null) list.animateScrollToItem(0)
    }
    val nowReading = stringResource(R.string.reader_now_reading)
    val readFromHere = stringResource(R.string.reader_read_from_here)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = readablePadding(padding, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Always NOTICE_ROWS items before the paragraphs, so scrolling to a
            // paragraph doesn't depend on whether a notice is showing.
            item { Notice(state, notice, onDismissNotice, onReadClipboardAnyway, reader, onOpenVoices) }
            itemsIndexed(state.paragraphs) { index, paragraph ->
                val current = index == state.index && state.status != ReadAloud.Status.FINISHED
                Text(
                    paragraph,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable(onClickLabel = readFromHere) {
                            reader.jumpTo(index)
                            reader.play() // no-op when already reading: the jump restarted it there
                        }
                        .semantics { if (current) stateDescription = nowReading }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Why a share wasn't read, why reading stopped, or what to know, above the text; empty when all is well. */
@Composable
private fun Notice(
    state: ReadAloud.State,
    share: ShareNotice?,
    onDismissShare: () -> Unit,
    onReadClipboardAnyway: () -> Unit,
    reader: ReadAloud,
    onOpenVoices: () -> Unit,
) {
    val problem = state.problem
    if (share == null && problem == null && !state.truncated) return
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (share != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)) {
                    Text(shareMessage(share, inPasteBox = false), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (share == ShareNotice.CLIPBOARD_SENSITIVE) {
                            TextButton(onClick = onReadClipboardAnyway) { Text(stringResource(R.string.action_read_anyway)) }
                        }
                        TextButton(onClick = onDismissShare) { Text(stringResource(R.string.action_dismiss)) }
                    }
                }
            }
        }
        if (problem != null || state.truncated) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (problem != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val message = when (problem) {
                        ReadAloud.Problem.NO_VOICE -> stringResource(R.string.reader_problem_no_voice)
                        ReadAloud.Problem.ENGINE -> stringResource(R.string.reader_problem_engine)
                        ReadAloud.Problem.AUDIO_BUSY -> stringResource(R.string.reader_problem_audio)
                        null -> stringResource(R.string.reader_truncated, NumberFormat.getIntegerInstance().format(ReaderText.MAX_CHARS))
                    }
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (problem) {
                            ReadAloud.Problem.NO_VOICE -> {
                                TextButton(onClick = onOpenVoices) { Text(stringResource(R.string.library_title)) }
                                TextButton(onClick = { reader.next() }) { Text(stringResource(R.string.action_skip)) }
                            }
                            ReadAloud.Problem.ENGINE, ReadAloud.Problem.AUDIO_BUSY ->
                                TextButton(onClick = { reader.play() }) { Text(stringResource(R.string.action_retry)) }
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderControls(state: ReadAloud.State, reader: ReadAloud) {
    val playing = state.status == ReadAloud.Status.PLAYING
    // Shown only when the wait lasts: between paragraphs computed ahead it
    // is a few milliseconds and mustn't flicker.
    val preparingShown = rememberLastingFlag(state.preparing)
    val preparingLabel = stringResource(R.string.reader_preparing)
    BottomAppBar {
        SpeedButton(state.rate, onPick = reader::setRate)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { reader.previous() }) {
            Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = stringResource(R.string.reader_previous))
        }
        Box(contentAlignment = Alignment.Center) {
            FilledIconButton(
                onClick = { reader.toggle() },
                modifier = Modifier
                    .size(56.dp)
                    .semantics { if (preparingShown) stateDescription = preparingLabel },
            ) {
                if (playing) {
                    Icon(painterResource(R.drawable.ic_pause), contentDescription = stringResource(R.string.reader_pause))
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.reader_play))
                }
            }
            // A ring around the button while the voice prepares the paragraph.
            if (preparingShown) CircularProgressIndicator(modifier = Modifier.size(60.dp), strokeWidth = 3.dp)
        }
        IconButton(onClick = { reader.next() }, enabled = state.index < state.paragraphs.lastIndex) {
            Icon(painterResource(R.drawable.ic_skip_next), contentDescription = stringResource(R.string.reader_next))
        }
        Spacer(Modifier.weight(1f))
        Text(
            when {
                state.status == ReadAloud.Status.FINISHED -> stringResource(R.string.reader_finished)
                preparingShown -> preparingLabel
                else -> stringResource(R.string.reader_position, state.index + 1, state.paragraphs.size)
            },
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

/** "1.25×", opening the list of speeds. */
@Composable
private fun SpeedButton(rate: Float, onPick: (Float) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    // TalkBack says "Speed 1.25x" rather than just "1.25 times".
    val label = stringResource(R.string.try_speed, formatSpeed(rate))
    val menuLabel = stringResource(R.string.reader_speed_menu)
    Box { // the menu opens at the button
        TextButton(
            onClick = { open = true },
            modifier = Modifier.padding(start = 4.dp).semantics { contentDescription = label },
        ) {
            Text(formatSpeed(rate) + "×")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(menuLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            for (option in ReadAloud.RATES) {
                DropdownMenuItem(
                    text = { Text(formatSpeed(option) + "×") },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                )
            }
        }
    }
}

/**
 * The voices that can read the current paragraph's language, as on Home
 * (speed on this phone included); picking one makes it that language's
 * default and the paragraph starts over in it. [locale] null: no installed
 * voice reads the paragraph, so the choice offers the voice library.
 */
@Composable
private fun VoiceButton(
    locale: Locale?,
    voicesFor: (Locale) -> List<VoiceRow>,
    onChoose: (VoiceRow) -> Unit,
    onOpenVoices: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(painterResource(R.drawable.ic_record_voice_over), contentDescription = stringResource(R.string.reader_voice))
    }
    if (!open) return
    val language = locale?.displayName
    val rows = if (locale != null) voicesFor(locale) else emptyList()
    AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_close)) } },
        dismissButton = if (rows.isEmpty()) {
            {
                TextButton(
                    onClick = {
                        open = false
                        onOpenVoices()
                    },
                ) { Text(stringResource(R.string.library_title)) }
            }
        } else {
            null
        },
        title = { Text(if (language != null) stringResource(R.string.reader_voice_title, language) else stringResource(R.string.reader_voice)) },
        text = {
            // Scrolls within the dialog: a language can have dozens of voices.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (rows.isEmpty() || language == null) {
                    Text(stringResource(R.string.reader_voice_none), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        stringResource(R.string.reader_voice_note, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    for (row in rows) {
                        VoiceChoiceRow(
                            row,
                            onClick = {
                                open = false
                                if (!row.isDefault) onChoose(row)
                            },
                        )
                    }
                }
            }
        },
    )
}

/** One voice in the choice: radio, initial, name, gender and pack, speed. The whole row is the target. */
@Composable
private fun VoiceChoiceRow(row: VoiceRow, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = row.isDefault, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = row.isDefault, onClick = null)
        VoiceAvatar(row)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(row.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                genderName(row.gender) + " · " + row.modelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.speed?.let { SpeedLabel(it) }
        }
    }
}

/**
 * The moon, or with a timer set the moon and the time reading stops ("23:45",
 * a clock time, so nothing ticks); opens the choices. The notification shows
 * the same time.
 */
@Composable
private fun SleepTimerButton(sleepAt: Long?, onSet: (Int?) -> Unit) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val until = sleepAt?.let { formatSleepTime(context, it) }
    val label = if (until != null) stringResource(R.string.reader_sleep_active, until) else stringResource(R.string.reader_sleep_timer)
    Box { // the menu opens at the button
        if (until == null) {
            IconButton(onClick = { open = true }) {
                Icon(painterResource(R.drawable.ic_bedtime), contentDescription = label)
            }
        } else {
            TextButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = label }) {
                Icon(painterResource(R.drawable.ic_bedtime), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(until)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.reader_sleep_timer), style = MaterialTheme.typography.labelMedium)
                Text(
                    if (until != null) stringResource(R.string.reader_sleep_until, until) else stringResource(R.string.reader_sleep_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (minutes in ReadAloud.SLEEP_MINUTES) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_sleep_minutes, minutes)) },
                    onClick = {
                        open = false
                        onSet(minutes)
                    },
                )
            }
            if (sleepAt != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_sleep_off)) },
                    onClick = {
                        open = false
                        onSet(null)
                    },
                )
            }
        }
    }
}

/** What to say about a share that wasn't read: above a text, the paste field isn't there to point to. */
@Composable
private fun shareMessage(notice: ShareNotice, inPasteBox: Boolean): String = when (notice) {
    ShareNotice.LINK_ONLY -> stringResource(R.string.reader_link_only)
    ShareNotice.UNREADABLE_FILE ->
        stringResource(if (inPasteBox) R.string.reader_file_unreadable else R.string.reader_file_unreadable_kept)
    ShareNotice.CLIPBOARD_EMPTY -> stringResource(R.string.reader_clipboard_empty)
    ShareNotice.CLIPBOARD_SENSITIVE -> stringResource(R.string.reader_clipboard_sensitive)
}

/**
 * Why a text handed to the reader (a share, a selection, the clipboard)
 * wasn't read. The text the reader held, if any, stays, and so does its
 * reading.
 */
enum class ShareNotice {
    /** A web address alone: Chrome shares pages that way. */
    LINK_ONLY,

    /** A shared file that couldn't be read, or isn't text. */
    UNREADABLE_FILE,

    /** "Read copied text" with no text on the clipboard. */
    CLIPBOARD_EMPTY,

    /** "Read copied text" with a clip its app marked sensitive (a password). */
    CLIPBOARD_SENSITIVE,
}

/** Opened from Home, or from a share with nothing to read: paste or type, then read. */
@Composable
private fun PasteBox(
    notice: ShareNotice?,
    padding: PaddingValues,
    onRead: (String) -> Unit,
    onReadClipboardAnyway: () -> Unit,
    /** Brings back the text "Read something else" just put away; null when there is none. */
    onBackToPrevious: (() -> Unit)?,
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                // As on Home: the Scaffold's padding is applied here and marked
                // consumed, so imePadding adds only what the keyboard covers
                // beyond the navigation bar.
                .consumeWindowInsets(padding)
                .padding(readablePadding(padding, top = 16.dp, bottom = 16.dp))
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (notice != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(shareMessage(notice, inPasteBox = true))
                        if (notice == ShareNotice.CLIPBOARD_SENSITIVE) {
                            TextButton(onClick = onReadClipboardAnyway) { Text(stringResource(R.string.action_read_anyway)) }
                        }
                    }
                }
            }
            Text(stringResource(R.string.reader_tip), style = MaterialTheme.typography.bodyMedium)
            if (onBackToPrevious != null) {
                TextButton(onClick = onBackToPrevious) { Text(stringResource(R.string.action_back_to_previous_text)) }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.reader_text_label)) },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                minLines = 6,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tapped by the user for this very clip: a sensitive one is pasted too.
                OutlinedButton(
                    onClick = {
                        (ClipboardText.read(context, allowSensitive = true) as? ClipboardText.Result.Text)?.let { text = it.text.toString() }
                    },
                ) {
                    Text(stringResource(R.string.action_paste))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onRead(text) }, enabled = text.isNotBlank()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.reader_play))
                }
            }
        }
    }
}

/** Rows before the first paragraph in the list (the notice). */
private const val NOTICE_ROWS = 1


/** 1f -> "1", 1.25f -> "1.25", 0.75f -> "0.75". */
private fun formatSpeed(rate: Float): String =
    String.format(Locale.US, "%.2f", rate).trimEnd('0').trimEnd('.')
