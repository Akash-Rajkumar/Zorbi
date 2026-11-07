package com.zorbi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Dark-only Material 3 Color Scheme matching Master Prompt tokens
private val DarkColorScheme = darkColorScheme(
    primary = ZorbiPrimaryCyan,
    onPrimary = Color(0xFF001520),
    primaryContainer = Color(0xFF003644),
    onPrimaryContainer = ZorbiPrimaryCyan,
    secondary = ZorbiAccentViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2E1B5B),
    onSecondaryContainer = Color(0xFFE8DDFF),
    tertiary = ZorbiSuccess,
    onTertiary = Color(0xFF00220E),
    background = ZorbiBackground,
    onBackground = ZorbiTextPrimary,
    surface = ZorbiSurface,
    onSurface = ZorbiTextPrimary,
    surfaceVariant = ZorbiSurfaceVariant,
    onSurfaceVariant = ZorbiTextMuted,
    error = ZorbiError,
    onError = Color.White,
    outline = ZorbiBorder
)

@Composable
fun ZorbiTheme(
    content: @Composable () -> Unit
) {
    val palette = ZorbiColorPalette()
    val spacing = ZorbiSpacing()

    CompositionLocalProvider(
        LocalZorbiColors provides palette,
        LocalZorbiSpacing provides spacing
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = ZorbiTypography,
            shapes = ZorbiShapes,
            content = content
        )
    }
}

object ZorbiTheme {
    val colors: ZorbiColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalZorbiColors.current

    val spacing: ZorbiSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalZorbiSpacing.current

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes
}
