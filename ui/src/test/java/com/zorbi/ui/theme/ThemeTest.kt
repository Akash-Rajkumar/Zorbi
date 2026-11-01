package com.zorbi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit verification for Zorbi Design Tokens per Master Prompt Section 13 (FROZEN).
 */
class ThemeTest {

    @Test
    fun verifyFrozenColorTokens() {
        // Master Prompt Section 13 Design Tokens
        assertThat(ZorbiBackground).isEqualTo(Color(0xFF0A0E1A))
        assertThat(ZorbiSurface).isEqualTo(Color(0xFF121830))
        assertThat(ZorbiPrimaryCyan).isEqualTo(Color(0xFF00E5FF))
        assertThat(ZorbiAccentViolet).isEqualTo(Color(0xFF7C4DFF))
        assertThat(ZorbiSuccess).isEqualTo(Color(0xFF00E676))
        assertThat(ZorbiWarning).isEqualTo(Color(0xFFFFB300))
        assertThat(ZorbiError).isEqualTo(Color(0xFFFF5252))
        assertThat(ZorbiTextPrimary).isEqualTo(Color(0xFFE6EDF7))
        assertThat(ZorbiTextMuted).isEqualTo(Color(0xFF8B97B5))
        assertThat(ZorbiBorder).isEqualTo(Color(0xFF1E2A4A))
    }

    @Test
    fun verifyElevatedGlassAlpha() {
        // Elevated glass cards: surface at ~70% alpha (Section 13)
        val alpha = ZorbiSurfaceElevated.alpha
        assertThat(alpha).isAtLeast(0.68f)
        assertThat(alpha).isAtMost(0.72f)
    }

    @Test
    fun verifyAccessibilityTouchTarget() {
        val spacing = ZorbiSpacing()
        // Minimum 48dp touch target per Section 13 & Android accessibility standards
        assertThat(spacing.minTouchTarget).isAtLeast(48.dp)
        assertThat(spacing.lg).isEqualTo(16.dp) // Standard 16dp gutter
        assertThat(spacing.sm).isEqualTo(8.dp)  // 8dp grid unit
    }

    @Test
    fun verifySciFiShapesAndAccents() {
        assertThat(SciFiAngledTabShape).isNotNull()
        assertThat(SciFiChamferedPanelShape).isNotNull()
        assertThat(ZorbiNeonMagenta).isEqualTo(Color(0xFFE040FB))
    }
}
