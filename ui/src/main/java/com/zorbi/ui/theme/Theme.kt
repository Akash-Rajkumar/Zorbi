package com.zorbi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// UI / UX SPEC colors per Section 13 (FROZEN)
val ZorbiBackground = Color(0xFF0A0E1A)
val ZorbiSurface = Color(0xFF121830)
val ZorbiPrimaryCyan = Color(0xFF00E5FF)
val ZorbiAccentViolet = Color(0xFF7C4DFF)
val ZorbiSuccess = Color(0xFF00E676)
val ZorbiWarning = Color(0xFFFFB300)
val ZorbiError = Color(0xFFFF5252)
val ZorbiText = Color(0xFFE6EDF7)
val ZorbiMuted = Color(0xFF8B97B5)

private val DarkColorScheme = darkColorScheme(
    primary = ZorbiPrimaryCyan,
    secondary = ZorbiAccentViolet,
    background = ZorbiBackground,
    surface = ZorbiSurface,
    error = ZorbiError,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = ZorbiText,
    onSurface = ZorbiText,
    onError = Color.White
)

@Composable
fun ZorbiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

