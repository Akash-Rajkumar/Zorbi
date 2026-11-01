package com.zorbi.ui.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.zorbi.core.Dir
import com.zorbi.core.Slash
import com.zorbi.core.Swipe
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiGlowCyan
import com.zorbi.ui.theme.ZorbiPrimaryCyan

/**
 * Renders glowing slash trails and swipe ripple animations over the screen.
 * Per Master Prompt Section 13 ("trail for slashes, ripple on gestures").
 */
@Composable
fun GestureRippleOverlay(
    activeVisual: ActiveGestureVisual?,
    modifier: Modifier = Modifier
) {
    if (activeVisual == null) return

    val fadeAnim = remember(activeVisual.createdAtNanos) { Animatable(1f) }

    LaunchedEffect(activeVisual.createdAtNanos) {
        fadeAnim.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = activeVisual.lifetimeMs.toInt())
        )
    }

    val currentAlpha = fadeAnim.value
    if (currentAlpha <= 0.01f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        when (val gesture = activeVisual.gesture) {
            is Slash -> {
                val pts = gesture.path
                if (pts.size >= 2) {
                    val path = Path()
                    val firstOffset = Offset(pts[0].x * canvasWidth, pts[0].y * canvasHeight)
                    path.moveTo(firstOffset.x, firstOffset.y)

                    for (i in 1 until pts.size) {
                        val pt = pts[i]
                        path.lineTo(pt.x * canvasWidth, pt.y * canvasHeight)
                    }

                    // Outer neon glow
                    drawPath(
                        path = path,
                        color = ZorbiPrimaryCyan.copy(alpha = currentAlpha * 0.4f),
                        style = Stroke(
                            width = 12f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Core laser slash line
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = currentAlpha),
                        style = Stroke(
                            width = 4f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            is Swipe -> {
                // Expanding ripple for lane swipes
                val centerX = canvasWidth * 0.5f
                val centerY = canvasHeight * 0.7f

                val (dx, dy) = when (gesture.dir) {
                    Dir.LEFT -> -150f to 0f
                    Dir.RIGHT -> 150f to 0f
                    Dir.UP -> 0f to -150f
                    Dir.DOWN -> 0f to 150f
                }

                val progress = 1f - currentAlpha
                val targetOffset = Offset(centerX + dx * progress, centerY + dy * progress)

                // Expanding ripple circle
                drawCircle(
                    color = ZorbiAccentViolet.copy(alpha = currentAlpha * 0.5f),
                    radius = 30f + 50f * progress,
                    center = targetOffset,
                    style = Stroke(width = 4f)
                )

                // Velocity streak
                drawLine(
                    color = ZorbiPrimaryCyan.copy(alpha = currentAlpha * 0.7f),
                    start = Offset(centerX, centerY),
                    end = targetOffset,
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }

            else -> {
                // Other gestures handled by status indicators
            }
        }
    }
}
