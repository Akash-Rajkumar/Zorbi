package com.zorbi.ui.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zorbi.ui.theme.ZorbiCardShape
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import kotlinx.coroutines.launch

/**
 * Reusable visual animation extensions for the ZORBI gaming UI.
 * Lightweight, performant, and respectful of reduced motion.
 */

/**
 * Creates a subtle ambient breathing neon glow behind a composable.
 */
fun Modifier.pulsingGlow(
    color: Color = ZorbiPrimaryCyan,
    minAlpha: Float = 0.08f,
    maxAlpha: Float = 0.28f,
    durationMs: Int = 2200
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingGlow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    this.drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = size.maxDimension * 0.75f
            )
        )
    }
}

/**
 * Animates a glowing pulsing border around a card or button.
 */
fun Modifier.pulsingBorder(
    color: Color = ZorbiPrimaryCyan,
    shape: Shape = ZorbiCardShape,
    borderWidth: Dp = 1.5.dp,
    durationMs: Int = 1800
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingBorder")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    this.border(
        border = BorderStroke(borderWidth, color.copy(alpha = alpha)),
        shape = shape
    )
}

/**
 * Adds an interactive spring-based scale compression (to ~0.97f) when pressed,
 * providing tactile visual feedback without lag.
 */
fun Modifier.interactiveBounce(
    targetScale: Float = 0.965f,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this

    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    this
        .scale(scale.value)
        .pointerInput(enabled) {
            awaitEachGesture {
                awaitFirstDown()
                scope.launch {
                    scale.animateTo(
                        targetValue = targetScale,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f)
                    )
                }
                waitForUpOrCancellation()
                scope.launch {
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f)
                    )
                }
            }
        }
}

/**
 * High-tech diagonal gaming shimmer sweep across loading indicators and skeleton elements.
 */
fun Modifier.gamingShimmer(
    highlightColor: Color = ZorbiPrimaryCyan.copy(alpha = 0.25f),
    durationMs: Int = 1600
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmerTransition")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    this.drawBehind {
        val width = size.width
        val height = size.height
        val startX = (progress * (width * 2)) - width

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    highlightColor,
                    Color.Transparent
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + width * 0.6f, height)
            )
        )
    }
}
