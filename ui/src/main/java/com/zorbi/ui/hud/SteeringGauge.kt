package com.zorbi.ui.hud

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Futuristic HUD Steering Gauge for two-hand wheel and pose-lean steering modes.
 * Displays real-time steering value [-1.0..+1.0] and angle in degrees.
 * Per Master Prompt Section 13 ("steering gauge for the wheel/lean").
 */
@Composable
fun SteeringGauge(
    steerValue: Float,
    angleDeg: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    // Smooth angle transition
    val clampedValue = steerValue.coerceIn(-1f, 1f)
    val animatedValue by animateFloatAsState(
        targetValue = clampedValue,
        animationSpec = tween(120),
        label = "steerValue"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = 6.dp.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            // Background arc spanning 180 degrees (from 180 to 360)
            drawArc(
                color = ZorbiSurfaceElevated,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Center deadzone marker (around 270 degrees)
            drawArc(
                color = Color(0x6600E5FF),
                startAngle = 265f,
                sweepAngle = 10f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx + 2f, cap = StrokeCap.Square)
            )

            // Dynamic steering arc from center (270°) to current angle
            val steerSweep = animatedValue * 80f // Up to 80 degrees left or right
            val steerColor = if (kotlin.math.abs(animatedValue) > 0.05f) ZorbiAccentViolet else ZorbiPrimaryCyan

            drawArc(
                color = steerColor,
                startAngle = 270f,
                sweepAngle = steerSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx + 1f, cap = StrokeCap.Round)
            )

            // Center needle / tick mark
            val needleAngleRad = Math.toRadians((270f + steerSweep).toDouble())
            val centerX = this.size.width / 2f
            val centerY = this.size.height / 2f
            val radius = (this.size.width - strokePx) / 2f

            val needleEnd = Offset(
                x = (centerX + radius * cos(needleAngleRad)).toFloat(),
                y = (centerY + radius * sin(needleAngleRad)).toFloat()
            )

            drawCircle(
                color = ZorbiPrimaryCyan,
                radius = 5.dp.toPx(),
                center = needleEnd
            )
        }

        // Numerical readout in center
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val direction = if (animatedValue > 0.05f) "R" else if (animatedValue < -0.05f) "L" else "—"
            Text(
                text = "${direction} ${String.format("%.1f", angleDeg)}°",
                style = ZorbiMetricTypography.copy(
                    fontSize = 11.sp,
                    color = ZorbiPrimaryCyan
                )
            )
            Text(
                text = "STEER",
                style = ZorbiTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    color = ZorbiTextMuted
                )
            )
        }
    }
}
