package com.zorbi.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.EngineState
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiChipShape
import com.zorbi.ui.theme.ZorbiError
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextDisabled
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme
import com.zorbi.ui.theme.ZorbiWarning

enum class StatusLevel {
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL
}

fun StatusLevel.toColor(): Color = when (this) {
    StatusLevel.SUCCESS -> ZorbiSuccess
    StatusLevel.WARNING -> ZorbiWarning
    StatusLevel.ERROR -> ZorbiError
    StatusLevel.NEUTRAL -> ZorbiTextMuted
}

/**
 * Futuristic glowing status dot with optional pulsing animation.
 */
@Composable
fun ZorbiStatusDot(
    level: StatusLevel,
    modifier: Modifier = Modifier,
    animatePulse: Boolean = true
) {
    val color = level.toColor()

    if (animatePulse && (level == StatusLevel.SUCCESS || level == StatusLevel.WARNING)) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotScale"
        )
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotAlpha"
        )

        Box(
            modifier = modifier.size(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(color)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * Status chip for system prerequisites (Camera, Accessibility, Battery).
 * Turns green only when truly granted (Master Prompt Section 8 / P3-2).
 */
@Composable
fun ZorbiStatusChip(
    label: String,
    isGranted: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val level = if (isGranted) StatusLevel.SUCCESS else StatusLevel.WARNING
    val color = level.toColor()
    val interactionSource = remember { MutableInteractionSource() }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = color),
            role = Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(ZorbiChipShape)
            .then(clickableModifier),
        shape = ZorbiChipShape,
        color = ZorbiSurfaceElevated,
        border = BorderStroke(1.dp, if (isGranted) color.copy(alpha = 0.5f) else ZorbiBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZorbiStatusDot(level = level, animatePulse = isGranted)
            Spacer(modifier = Modifier.width(8.dp))
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) ZorbiTextPrimary else ZorbiTextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = ZorbiTheme.typography.labelSmall.copy(
                    color = if (isGranted) ZorbiTextPrimary else ZorbiTextMuted
                )
            )
        }
    }
}

/**
 * High-performance HUD diagnostic chip for real-time FPS and latency display.
 * Accurately displays measured telemetry or labels simulated/unconnected status.
 */
@Composable
fun ZorbiFpsChip(
    fps: Float,
    latencyMs: Float,
    modifier: Modifier = Modifier,
    isSimulated: Boolean = false
) {
    val hasSignal = fps > 0f
    Surface(
        modifier = modifier.clip(ZorbiChipShape),
        shape = ZorbiChipShape,
        color = Color(0xCC0A0E1A),
        border = BorderStroke(1.dp, ZorbiBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZorbiStatusDot(
                level = if (hasSignal && !isSimulated && fps >= 20f) StatusLevel.SUCCESS
                        else if (hasSignal && fps >= 12f) StatusLevel.WARNING
                        else StatusLevel.ERROR,
                animatePulse = hasSignal && !isSimulated
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (hasSignal) "${fps.toInt()} FPS" else "NO SIGNAL",
                style = ZorbiMetricTypography.copy(fontSize = 11.sp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (!hasSignal) {
                Text(
                    text = "[NOT CONNECTED]",
                    style = ZorbiMetricTypography.copy(
                        fontSize = 9.sp,
                        color = ZorbiWarning
                    )
                )
            } else if (isSimulated) {
                Text(
                    text = "[DEMO]",
                    style = ZorbiMetricTypography.copy(
                        fontSize = 9.sp,
                        color = ZorbiWarning
                    )
                )
            } else {
                Text(
                    text = "${latencyMs.toInt()} ms",
                    style = ZorbiMetricTypography.copy(
                        fontSize = 11.sp,
                        color = ZorbiAccentViolet
                    )
                )
            }
        }
    }
}

/**
 * State badge for Engine state (IDLE, CALIBRATING, RUNNING, PAUSED, ERROR).
 */
@Composable
fun ZorbiEngineStateBadge(
    state: EngineState,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (state) {
        EngineState.IDLE -> "IDLE" to ZorbiTextMuted
        EngineState.CALIBRATING -> "CALIBRATING" to ZorbiWarning
        EngineState.RUNNING -> "ACTIVE" to ZorbiSuccess
        EngineState.PAUSED -> "PAUSED" to ZorbiAccentViolet
        EngineState.ERROR -> "ERROR" to ZorbiError
    }

    Surface(
        modifier = modifier.clip(ZorbiChipShape),
        shape = ZorbiChipShape,
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = ZorbiTheme.typography.labelSmall.copy(
                color = color,
                fontSize = 10.sp
            )
        )
    }
}
