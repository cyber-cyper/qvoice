package com.riniso.qvoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.riniso.qvoice.R
import com.riniso.qvoice.engine.SpeedEstimate
import com.riniso.qvoice.engine.SpeedTier
import com.riniso.qvoice.voices.Gender

// The pieces a voice is shown with, wherever voices are listed: Home's voice
// list and the reader's voice choice look alike on purpose.

/** "Female", "Male" or "Voice" (unknown). */
@Composable
internal fun genderName(gender: Gender): String = when (gender) {
    Gender.FEMALE -> stringResource(R.string.gender_female)
    Gender.MALE -> stringResource(R.string.gender_male)
    Gender.UNKNOWN -> stringResource(R.string.gender_unknown)
}

/** How fast the voice runs on this phone, in a few words and a colour (slow in the error colour). */
@Composable
internal fun SpeedLabel(speed: SpeedEstimate) {
    val (label, color) = when (speed.tier) {
        SpeedTier.FAST -> stringResource(R.string.speed_short_fast) to MaterialTheme.colorScheme.primary
        SpeedTier.KEEPS_UP -> stringResource(R.string.speed_short_keeps_up) to MaterialTheme.colorScheme.onSurfaceVariant
        SpeedTier.SLOW -> stringResource(R.string.speed_short_slow) to MaterialTheme.colorScheme.error
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
}

/** The initial in a gender-coloured circle. Decorative: the name beside it is what TalkBack reads. */
@Composable
internal fun VoiceAvatar(row: VoiceRow) {
    val (container, content) = when (row.gender) {
        Gender.FEMALE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        Gender.MALE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        Gender.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(container).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(row.displayName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = content)
    }
}
