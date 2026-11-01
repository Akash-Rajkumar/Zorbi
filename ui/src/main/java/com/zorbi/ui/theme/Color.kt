package com.zorbi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// UI / UX SPEC colors per Master Prompt Section 13 (FROZEN)
val ZorbiBackground = Color(0xFF0A0E1A)
val ZorbiSurface = Color(0xFF121830)
val ZorbiSurfaceElevated = Color(0xB3121830) // Surface at ~70% alpha for elevated glass cards
val ZorbiSurfaceVariant = Color(0xFF1A223F)
val ZorbiBorder = Color(0xFF1E2A4A)

// Brand and semantic accents
val ZorbiPrimaryCyan = Color(0xFF00E5FF)
val ZorbiAccentViolet = Color(0xFF7C4DFF)
val ZorbiNeonMagenta = Color(0xFFE040FB)
val ZorbiElectricPurple = Color(0xFFD500F9)
val ZorbiSuccess = Color(0xFF00E676)
val ZorbiWarning = Color(0xFFFFB300)
val ZorbiError = Color(0xFFFF5252)

// Text tokens
val ZorbiTextPrimary = Color(0xFFE6EDF7)
val ZorbiTextMuted = Color(0xFF8B97B5)
val ZorbiTextDisabled = Color(0xFF5A6682)

// Glow & HUD accents
val ZorbiGlowCyan = Color(0x3300E5FF)
val ZorbiGlowViolet = Color(0x337C4DFF)
val ZorbiOverlayScrim = Color(0xCC0A0E1A)

@Immutable
data class ZorbiColorPalette(
    val background: Color = ZorbiBackground,
    val surface: Color = ZorbiSurface,
    val surfaceElevated: Color = ZorbiSurfaceElevated,
    val surfaceVariant: Color = ZorbiSurfaceVariant,
    val border: Color = ZorbiBorder,
    val primaryCyan: Color = ZorbiPrimaryCyan,
    val accentViolet: Color = ZorbiAccentViolet,
    val success: Color = ZorbiSuccess,
    val warning: Color = ZorbiWarning,
    val error: Color = ZorbiError,
    val textPrimary: Color = ZorbiTextPrimary,
    val textMuted: Color = ZorbiTextMuted,
    val textDisabled: Color = ZorbiTextDisabled,
    val glowCyan: Color = ZorbiGlowCyan,
    val glowViolet: Color = ZorbiGlowViolet,
    val overlayScrim: Color = ZorbiOverlayScrim
)

val LocalZorbiColors = staticCompositionLocalOf { ZorbiColorPalette() }
