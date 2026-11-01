package com.zorbi.ui.screens.calibrator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.HandsFrame
import com.zorbi.core.PoseFrame
import com.zorbi.core.Pt
import com.zorbi.core.TrackerFrame
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiCardShape
import com.zorbi.ui.theme.ZorbiGlowCyan
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextMuted

// Canonical MediaPipe Hand 21-landmark connection pairs
val HAND_CONNECTIONS = listOf(
    // Palm base
    0 to 1, 0 to 5, 0 to 17, 5 to 9, 9 to 13, 13 to 17,
    // Thumb
    1 to 2, 2 to 3, 3 to 4,
    // Index finger
    5 to 6, 6 to 7, 7 to 8,
    // Middle finger
    9 to 10, 10 to 11, 11 to 12,
    // Ring finger
    13 to 14, 14 to 15, 15 to 16,
    // Pinky
    17 to 18, 18 to 19, 19 to 20
)

// Upper-body pose connections for POSE_LITE
val POSE_CONNECTIONS = listOf(
    11 to 12, // Shoulders
    11 to 13, 13 to 15, // Left arm
    12 to 14, 14 to 16, // Right arm
    11 to 23, 12 to 24, 23 to 24 // Torso
)

/**
 * Live neon skeleton canvas driven directly by TrackerFrame landmarks.
 * Points are normalized 0..1 per Master Prompt Section 5.1 conventions.
 */
@Composable
fun SkeletonCanvas(
    frame: TrackerFrame?,
    modifier: Modifier = Modifier,
    isTrackingActive: Boolean = true
) {
    Box(
        modifier = modifier
            .clip(ZorbiCardShape)
            .background(Color(0xFF070B14))
            .border(1.dp, ZorbiBorder, ZorbiCardShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Helper to transform normalized image-space point into canvas coordinates
            fun Pt.toOffset(): Offset = Offset(x * canvasWidth, y * canvasHeight)

            when (frame) {
                is HandsFrame -> {
                    for (hand in frame.hands) {
                        val pts = hand.landmarks
                        if (pts.size >= 21) {
                            // Draw neon bones
                            for ((startIdx, endIdx) in HAND_CONNECTIONS) {
                                val start = pts[startIdx].toOffset()
                                val end = pts[endIdx].toOffset()

                                // Soft glow layer
                                drawLine(
                                    color = ZorbiGlowCyan,
                                    start = start,
                                    end = end,
                                    strokeWidth = 8f,
                                    cap = StrokeCap.Round
                                )
                                // Core neon bone
                                drawLine(
                                    color = ZorbiPrimaryCyan,
                                    start = start,
                                    end = end,
                                    strokeWidth = 3f,
                                    cap = StrokeCap.Round
                                )
                            }

                            // Draw joints
                            for ((index, pt) in pts.withIndex()) {
                                val center = pt.toOffset()
                                val jointColor = if (index in listOf(4, 8, 12, 16, 20)) {
                                    ZorbiAccentViolet // Fingertips highlighted in violet
                                } else if (index == 0) {
                                    ZorbiSuccess // Wrist anchor in success green
                                } else {
                                    ZorbiPrimaryCyan
                                }

                                drawCircle(
                                    color = jointColor,
                                    radius = 5f,
                                    center = center
                                )
                            }
                        }
                    }
                }

                is PoseFrame -> {
                    val pts = frame.landmarks
                    if (pts.size >= 25) {
                        for ((startIdx, endIdx) in POSE_CONNECTIONS) {
                            if (startIdx < pts.size && endIdx < pts.size) {
                                val start = pts[startIdx].toOffset()
                                val end = pts[endIdx].toOffset()

                                drawLine(
                                    color = ZorbiGlowCyan,
                                    start = start,
                                    end = end,
                                    strokeWidth = 8f,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = ZorbiAccentViolet,
                                    start = start,
                                    end = end,
                                    strokeWidth = 3.5f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        for ((index, pt) in pts.withIndex()) {
                            if (index in 11..16 || index in 23..24) {
                                drawCircle(
                                    color = ZorbiPrimaryCyan,
                                    radius = 6f,
                                    center = pt.toOffset()
                                )
                            }
                        }
                    }
                }

                null -> {
                    // No frame yet
                }
            }
        }

        // Overlay status text if no landmarks are visible
        if (frame == null || !isTrackingActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x990A0E1A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ALIGN HAND IN FRONT OF CAMERA",
                    style = ZorbiMetricTypography.copy(
                        fontSize = 12.sp,
                        color = ZorbiTextMuted
                    )
                )
            }
        }
    }
}
