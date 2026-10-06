package com.riniso.qvoice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.riniso.qvoice.R

/** What a help answer's button does. */
private enum class HelpAction { TTS_SETTINGS, APP_SETTINGS, VOICES, PRIVACY }

/** One question and its answer (string resources), with an optional button that does what the answer says. */
private class HelpItem(val question: Int, val answer: Int, val action: HelpAction? = null)

/**
 * The questions users of a third-party speech engine and reader ask most
 * (D-053), in the order people meet them: making QVoice the phone's voice,
 * having text read, then what goes wrong. Offline, like the rest of the app.
 */
private val HELP = listOf(
    HelpItem(R.string.help_q_engine, R.string.help_a_engine, HelpAction.TTS_SETTINGS),
    HelpItem(R.string.help_q_read, R.string.help_a_read),
    HelpItem(R.string.help_q_other_app, R.string.help_a_other_app, HelpAction.TTS_SETTINGS),
    HelpItem(R.string.help_q_screen_off, R.string.help_a_screen_off, HelpAction.APP_SETTINGS),
    HelpItem(R.string.help_q_talkback, R.string.help_a_talkback, HelpAction.VOICES),
    HelpItem(R.string.help_q_language, R.string.help_a_language, HelpAction.VOICES),
    HelpItem(R.string.help_q_private, R.string.help_a_private, HelpAction.PRIVACY),
    HelpItem(R.string.help_q_clipboard, R.string.help_a_clipboard),
    HelpItem(R.string.help_q_storage, R.string.help_a_storage, HelpAction.VOICES),
)

/**
 * Help: the questions as a list, one answer open at a time (the list stays
 * short enough to scan), each with the button that does what it says, and
 * Send feedback at the end for anything else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit, onOpenVoices: () -> Unit) {
    val context = LocalContext.current
    // The open answer's index (-1: none); an Int survives rotation as it is.
    var open by rememberSaveable { mutableIntStateOf(-1) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = readablePadding(padding, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.help_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                itemsIndexed(HELP) { index, item ->
                    HelpEntry(
                        item = item,
                        expanded = open == index,
                        onToggle = { open = if (open == index) -1 else index },
                        onAction = { action ->
                            when (action) {
                                HelpAction.TTS_SETTINGS -> openTtsSettings(context)
                                HelpAction.APP_SETTINGS -> openAppSettings(context)
                                HelpAction.VOICES -> onOpenVoices()
                                HelpAction.PRIVACY -> openUrl(context, Links.PRIVACY_POLICY)
                            }
                        },
                    )
                }
                item {
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.help_more_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.help_more_body), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { sendFeedback(context) }) { Text(stringResource(R.string.action_send_feedback)) }
                    }
                }
            }
        }
    }
}

/**
 * A question that opens its answer. The question row is the target and a
 * heading (TalkBack can jump from question to question), and says whether
 * it is open; the answer's button is a separate target below it.
 */
@Composable
private fun HelpEntry(item: HelpItem, expanded: Boolean, onToggle: () -> Unit, onAction: (HelpAction) -> Unit) {
    val state = stringResource(if (expanded) R.string.help_state_open else R.string.help_state_closed)
    val toggleLabel = stringResource(if (expanded) R.string.help_hide_answer else R.string.help_show_answer)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = toggleLabel, onClick = onToggle)
                .semantics {
                    heading()
                    stateDescription = state
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(item.question), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        if (expanded) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(item.answer), style = MaterialTheme.typography.bodyMedium)
                item.action?.let { action ->
                    OutlinedButton(onClick = { onAction(action) }) {
                        Text(
                            stringResource(
                                when (action) {
                                    HelpAction.TTS_SETTINGS -> R.string.action_open_tts_settings
                                    HelpAction.APP_SETTINGS -> R.string.action_open_app_settings
                                    HelpAction.VOICES -> R.string.library_title
                                    HelpAction.PRIVACY -> R.string.action_privacy_policy
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}
