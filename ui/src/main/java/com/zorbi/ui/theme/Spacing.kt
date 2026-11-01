package com.zorbi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ZorbiSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,      // 8dp grid unit
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,     // 16dp standard gutter
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    val minTouchTarget: Dp = 48.dp // Accessibility guideline >= 48dp
)

val LocalZorbiSpacing = staticCompositionLocalOf { ZorbiSpacing() }
