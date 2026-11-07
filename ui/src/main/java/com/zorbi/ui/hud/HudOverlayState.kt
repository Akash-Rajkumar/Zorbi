package com.zorbi.ui.hud

import com.zorbi.core.GestureEvent
import com.zorbi.core.HudFrame
import com.zorbi.core.Pt
import com.zorbi.core.Slash
import com.zorbi.core.Steer
import com.zorbi.core.Swipe

/**
 * User-configurable opacity presets for the in-game HUD overlay.
 * Per Master Prompt Section 13 ("neon hand/pose skeleton at reduced opacity (toggle)").
 */
enum class HudOpacity(val label: String, val alpha: Float) {
    FULL("100%", 0.85f),
    SUBTLE("40%", 0.40f),
    MINIMAL("15%", 0.15f),
    HIDDEN("Off", 0.0f);

    fun next(): HudOpacity = when (this) {
        FULL -> SUBTLE
        SUBTLE -> MINIMAL
        MINIMAL -> HIDDEN
        HIDDEN -> FULL
    }
}

/**
 * Represents an active animated gesture ripple or stroke trail on screen.
 */
data class ActiveGestureVisual(
    val gesture: GestureEvent,
    val createdAtNanos: Long = System.nanoTime(),
    val lifetimeMs: Long = 600L
)

/**
 * State container for live HUD drawing.
 */
data class HudState(
    val currentFrame: HudFrame? = null,
    val opacity: HudOpacity = HudOpacity.SUBTLE,
    val isPaused: Boolean = false,
    val currentSteerValue: Float = 0f,
    val currentSteerAngleDeg: Float = 0f,
    val activeGesture: ActiveGestureVisual? = null
)
