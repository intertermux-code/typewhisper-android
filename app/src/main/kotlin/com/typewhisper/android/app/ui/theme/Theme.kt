package com.typewhisper.android.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF00695C)
private val TealBright = Color(0xFF5FDBC6)
private val TealDim = Color(0xFF00524A)
private val Slate = Color(0xFF4A6572)
private val Paper = Color(0xFFFAFAF7)
private val Ink = Color(0xFF1C1B1F)

private val LightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EFE2),
    onPrimaryContainer = TealDim,
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3E8),
    onSecondaryContainer = Color(0xFF1F2E35),
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6E4E0),
    onSurfaceVariant = Color(0xFF48464A),
    background = Paper,
    onBackground = Ink,
    error = Color(0xFF9C4146),
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = TealBright,
    onPrimary = TealDim,
    primaryContainer = Color(0xFF00453E),
    onPrimaryContainer = Color(0xFFB8EFE2),
    secondary = Color(0xFFAEC7D2),
    onSecondary = Color(0xFF1A2B33),
    secondaryContainer = Color(0xFF33454E),
    onSecondaryContainer = Color(0xFFD7E3E8),
    surface = Color(0xFF141413),
    onSurface = Color(0xFFE5E2E0),
    surfaceVariant = Color(0xFF48464A),
    onSurfaceVariant = Color(0xFFCAC4CF),
    background = Color(0xFF141413),
    onBackground = Color(0xFFE5E2E0),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

/**
 * One teal accent on paper and ink, light and dark. Every screen is Material 3 on top of
 * this scheme, so a card, a switch and an error line mean the same thing everywhere.
 */
@Composable
public fun TypeWhisperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
