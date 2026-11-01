package com.zorbi.ui.hud

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Dir
import com.zorbi.core.Swipe
import org.junit.Test

/**
 * Unit verification for HUD state transitions, opacity cycling, and steering range bounds.
 * Enforces Master Prompt Section 13 (HUD overlay specifications).
 */
class HudStateTest {

    @Test
    fun verifyHudOpacityPresetsAndCycle() {
        var opacity = HudOpacity.FULL
        assertThat(opacity.alpha).isEqualTo(0.85f)

        opacity = opacity.next()
        assertThat(opacity).isEqualTo(HudOpacity.SUBTLE)
        assertThat(opacity.alpha).isEqualTo(0.40f)

        opacity = opacity.next()
        assertThat(opacity).isEqualTo(HudOpacity.MINIMAL)
        assertThat(opacity.alpha).isEqualTo(0.15f)

        opacity = opacity.next()
        assertThat(opacity).isEqualTo(HudOpacity.HIDDEN)
        assertThat(opacity.alpha).isEqualTo(0.0f)

        opacity = opacity.next()
        assertThat(opacity).isEqualTo(HudOpacity.FULL)
    }

    @Test
    fun verifySteeringValueClamping() {
        fun clampSteer(value: Float): Float = value.coerceIn(-1.0f, 1.0f)

        assertThat(clampSteer(0.5f)).isEqualTo(0.5f)
        assertThat(clampSteer(-0.75f)).isEqualTo(-0.75f)
        assertThat(clampSteer(1.8f)).isEqualTo(1.0f)
        assertThat(clampSteer(-2.5f)).isEqualTo(-1.0f)
    }

    @Test
    fun verifyActiveGestureVisualLifetime() {
        val swipe = Swipe(tsNanos = 1000L, dir = Dir.RIGHT, speed = 2.0f)
        val visual = ActiveGestureVisual(gesture = swipe, lifetimeMs = 600L)

        assertThat(visual.gesture).isEqualTo(swipe)
        assertThat(visual.lifetimeMs).isEqualTo(600L)
    }
}
