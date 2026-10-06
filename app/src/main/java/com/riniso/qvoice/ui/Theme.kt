package com.riniso.qvoice.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * QVoice brand colours, taken from the logo: indigo (the speaker), violet
 * (the Q ring), cyan (the sound waves) and the deep navy behind them.
 *
 * Brand colours on every Android version, not the wallpaper-based dynamic
 * colours of Android 12+ (used until 0.3): the app is visited rarely (set up,
 * pick voices), so looking like its icon matters more than matching the
 * wallpaper — and Play screenshots then show what every user sees.
 *
 * Every role with a brand meaning is set, including the surface containers:
 * Material 3 falls back to its baseline purple for any role left out, and
 * cards (surfaceContainer*) would then clash. Only the error roles and the
 * scrim keep Material's defaults (a standard red and black, which suit the
 * palette). Text/background pairs meet WCAG AA (4.5:1) or better.
 */

private val Light = lightColorScheme(
    primary = Color(0xFF4C3FE0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3DFFF),
    onPrimaryContainer = Color(0xFF140C5C),
    inversePrimary = Color(0xFFC4BFFF),
    secondary = Color(0xFF0A7EA4),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC9F1FC),
    onSecondaryContainer = Color(0xFF002B3A),
    tertiary = Color(0xFF9B2FC9),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6DBFF),
    onTertiaryContainer = Color(0xFF34004D),
    background = Color(0xFFFBFAFF),
    onBackground = Color(0xFF1B1A2B),
    surface = Color(0xFFFBFAFF),
    onSurface = Color(0xFF1B1A2B),
    surfaceVariant = Color(0xFFE5E1F4),
    onSurfaceVariant = Color(0xFF474558),
    surfaceTint = Color(0xFF4C3FE0),
    inverseSurface = Color(0xFF302F42),
    inverseOnSurface = Color(0xFFF3F0FF),
    outline = Color(0xFF787590),
    outlineVariant = Color(0xFFC9C5DC),
    surfaceBright = Color(0xFFFBFAFF),
    surfaceDim = Color(0xFFDAD8E6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3FD),
    surfaceContainer = Color(0xFFEFEDF9),
    surfaceContainerHigh = Color(0xFFE9E7F4),
    surfaceContainerHighest = Color(0xFFE3E1EE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB9B2FF),
    onPrimary = Color(0xFF231A8C),
    primaryContainer = Color(0xFF3A2FC0),
    onPrimaryContainer = Color(0xFFE3DFFF),
    inversePrimary = Color(0xFF4C3FE0),
    secondary = Color(0xFF6ADCF7),
    onSecondary = Color(0xFF00374A),
    secondaryContainer = Color(0xFF004E68),
    onSecondaryContainer = Color(0xFFC9F1FC),
    tertiary = Color(0xFFE6A8FF),
    onTertiary = Color(0xFF4E0A6E),
    tertiaryContainer = Color(0xFF6E2A92),
    onTertiaryContainer = Color(0xFFF6DBFF),
    background = Color(0xFF0D0E26),
    onBackground = Color(0xFFE6E3F8),
    surface = Color(0xFF0D0E26),
    onSurface = Color(0xFFE6E3F8),
    surfaceVariant = Color(0xFF2A2A45),
    onSurfaceVariant = Color(0xFFC9C5DC),
    surfaceTint = Color(0xFFB9B2FF),
    inverseSurface = Color(0xFFE6E3F8),
    inverseOnSurface = Color(0xFF302F42),
    outline = Color(0xFF928FAA),
    outlineVariant = Color(0xFF474558),
    surfaceBright = Color(0xFF33354F),
    surfaceDim = Color(0xFF0D0E26),
    surfaceContainerLowest = Color(0xFF08091C),
    surfaceContainerLow = Color(0xFF14152E),
    surfaceContainer = Color(0xFF181A34),
    surfaceContainerHigh = Color(0xFF22243F),
    surfaceContainerHighest = Color(0xFF2D2F4A),
)

/** Material 3 in QVoice's brand colours, light or dark as the phone is set. */
@Composable
fun QVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
