package com.zorbi.ui.screens.calibrator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.HandsFrame
import com.zorbi.core.PoseFrame
import com.zorbi.core.Pt
import com.zorbi.core.TrackerFrame
import com.zorbi.ui.components.ExtractedHandData
import com.zorbi.ui.components.FingertipTrailPoint
import com.zorbi.ui.components.HandTrackingExtractor
import com.zorbi.ui.components.HandTrackingStatus
import com.zorbi.ui.components.HandTrackingStatusChip
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiCardShape
import com.zorbi.ui.theme.ZorbiError
import com.zorbi.ui.theme.ZorbiGlowCyan
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiWarning

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
 * Live hand tracking canvas.
 *
 * Implements the real-time visual hand-tracking indicator:
 * 1. Precision cyan marker that smoothly follows the detected index fingertip.
 * 2. Subtle fading movement trail showing recent fingertip velocity.
 * 3. Optional debug visualization mode displaying full 21 MediaPipe hand landmarks and connecting bones.
 * 4. Tracking status: TRACKING, HAND DETECTED, NO HAND DETECTED, CAMERA/TRACKING NOT CONNECTED.
 * 5. Small tracking confidence score when available from upstream tracking data.
 */
@Composable
fun SkeletonCanvas(
    frame: TrackerFrame?,
    modifier: Modifier = Modifier,
    isTrackingActive: Boolean = true,
    showFingertipMarker: Boolean = true,
    showFadingTrail: Boolean = true,
    showDebugBones: Boolean = false,
    confidenceScore: Float? = null,
    showStatusOverlay: Boolean = true,
    onStatusChange: ((HandTrackingStatus) -> Unit)? = null
) {
    // Extract live fingertip and tracking status
    val handData = remember(frame, isTrackingActive) {
        HandTrackingExtractor.extractFromTrackerFrame(frame, isTrackingActive)
    }

    LaunchedEffect(handData.status) {
        onStatusChange?.invoke(handData.status)
    }

    // Historical trail points with nanosecond timestamps
    val trailPoints = remember { mutableStateListOf<FingertipTrailPoint>() }

    // Smoothed fingertip position (Low-Pass Filter to eliminate sensor tremor without lag)
    var smoothedNormalizedPt by remember { mutableStateOf<Pt?>(null) }

    LaunchedEffect(handData.fingertipPt) {
        val target = handData.fingertipPt
        if (target != null) {
            val prev = smoothedNormalizedPt
            val next = if (prev == null) {
                target
            } else {
                // Responsive exponential smoothing (lerp factor 0.45)
                Pt(
                    x = prev.x + 0.45f * (target.x - prev.x),
                    y = prev.y + 0.45f * (target.y - prev.y),
                    z = prev.z + 0.45f * (target.z - prev.z)
                )
            }
            smoothedNormalizedPt = next

            if (showFadingTrail) {
                val nowNanos = System.nanoTime()
                trailPoints.add(
                    FingertipTrailPoint(
                        normalizedOffset = Offset(next.x, next.y),
                        timestampNanos = nowNanos
                    )
                )
                HandTrackingExtractor.pruneTrail(trailPoints, nowNanos)
            }
        } else {
            smoothedNormalizedPt = null
            trailPoints.clear()
        }
    }

    Box(
        modifier = modifier
            .clip(ZorbiCardShape)
            .background(Color(0xFF070B14))
            .border(1.dp, ZorbiBorder, ZorbiCardShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            fun Pt.toOffset(): Offset = Offset(x * canvasWidth, y * canvasHeight)
            fun Offset.toScreenOffset(): Offset = Offset(x * canvasWidth, y * canvasHeight)

            // 1. Optional Debug Bones & Joint Nodes
            if (showDebugBones) {
                when (frame) {
                    is HandsFrame -> {
                        for (hand in frame.hands) {
                            val pts = hand.landmarks
                            if (pts.size >= 21) {
                                // Draw neon bones
                                for ((startIdx, endIdx) in HAND_CONNECTIONS) {
                                    val start = pts[startIdx].toOffset()
                                    val end = pts[endIdx].toOffset()

                                    drawLine(
                                        color = ZorbiGlowCyan.copy(alpha = 0.4f),
                                        start = start,
                                        end = end,
                                        strokeWidth = 6f,
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = ZorbiPrimaryCyan.copy(alpha = 0.6f),
                                        start = start,
                                        end = end,
                                        strokeWidth = 2f,
                                        cap = StrokeCap.Round
                                    )
                                }

                                // Draw joint nodes
                                for ((index, pt) in pts.withIndex()) {
                                    val center = pt.toOffset()
                                    val jointColor = when (index) {
                                        8 -> ZorbiPrimaryCyan // Index fingertip
                                        4, 12, 16, 20 -> ZorbiAccentViolet // Other fingertips
                                        0 -> ZorbiSuccess // Wrist anchor
                                        else -> ZorbiPrimaryCyan.copy(alpha = 0.7f)
                                    }

                                    drawCircle(
                                        color = jointColor,
                                        radius = if (index == 8) 6f else 4f,
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
                                        color = ZorbiGlowCyan.copy(alpha = 0.4f),
                                        start = start,
                                        end = end,
                                        strokeWidth = 6f,
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = ZorbiAccentViolet.copy(alpha = 0.6f),
                                        start = start,
                                        end = end,
                                        strokeWidth = 2.5f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }

                            for ((index, pt) in pts.withIndex()) {
                                if (index in 11..16 || index in 23..24) {
                                    drawCircle(
                                        color = ZorbiPrimaryCyan,
                                        radius = 5f,
                                        center = pt.toOffset()
                                    )
                                }
                            }
                        }
                    }

                    null -> {}
                }
            }

            // 2. Fading Movement Trail
            if (showFadingTrail && trailPoints.size >= 2) {
                val nowNanos = System.nanoTime()
                val maxAgeNanos = 400_000_000L

                // Draw connecting fading trail segments
                for (i in 0 until trailPoints.size - 1) {
                    val p1 = trailPoints[i]
                    val p2 = trailPoints[i + 1]

                    val age1 = (nowNanos - p1.timestampNanos).coerceAtLeast(0L)
                    val progress = (1f - (age1.toFloat() / maxAgeNanos)).coerceIn(0f, 1f)
                    if (progress <= 0f) continue

                    val startOffset = p1.normalizedOffset.toScreenOffset()
                    val endOffset = p2.normalizedOffset.toScreenOffset()

                    val segmentAlpha = 0.65f * progress
                    val strokeWidth = 2f + (5f * progress)

                    // Soft glow under trail
                    drawLine(
                        color = ZorbiGlowCyan.copy(alpha = segmentAlpha * 0.5f),
                        start = startOffset,
                        end = endOffset,
                        strokeWidth = strokeWidth * 2.2f,
                        cap = StrokeCap.Round
                    )

                    // Core trail line
                    drawLine(
                        color = ZorbiPrimaryCyan.copy(alpha = segmentAlpha),
                        start = startOffset,
                        end = endOffset,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                // Draw trail bead particles
                for (trailPt in trailPoints) {
                    val age = (nowNanos - trailPt.timestampNanos).coerceAtLeast(0L)
                    val progress = (1f - (age.toFloat() / maxAgeNanos)).coerceIn(0f, 1f)
                    if (progress <= 0f) continue

                    val beadOffset = trailPt.normalizedOffset.toScreenOffset()
                    val beadAlpha = 0.5f * progress
                    val beadRadius = 1.5f + (3.5f * progress)

                    drawCircle(
                        color = ZorbiPrimaryCyan.copy(alpha = beadAlpha),
                        radius = beadRadius,
                        center = beadOffset
                    )
                }
            }

            // 3. Precision Cyan Tracking Marker at Index Fingertip
            if (showFingertipMarker && smoothedNormalizedPt != null) {
                val center = smoothedNormalizedPt!!.toOffset()

                // Layer A: Soft pulsing cyan glow
                drawCircle(
                    color = ZorbiGlowCyan.copy(alpha = 0.45f),
                    radius = 20f,
                    center = center
                )

                // Layer B: Precision cyan reticle ring
                drawCircle(
                    color = ZorbiPrimaryCyan,
                    radius = 11f,
                    center = center,
                    style = Stroke(width = 2.5f)
                )

                // Layer C: Subtle cardinal tick marks (crosshair precision gaming styling)
                val tickInner = 13f
                val tickOuter = 17f
                // North
                drawLine(
                    color = ZorbiPrimaryCyan.copy(alpha = 0.85f),
                    start = Offset(center.x, center.y - tickInner),
                    end = Offset(center.x, center.y - tickOuter),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
                // South
                drawLine(
                    color = ZorbiPrimaryCyan.copy(alpha = 0.85f),
                    start = Offset(center.x, center.y + tickInner),
                    end = Offset(center.x, center.y + tickOuter),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
                // West
                drawLine(
                    color = ZorbiPrimaryCyan.copy(alpha = 0.85f),
                    start = Offset(center.x - tickInner, center.y),
                    end = Offset(center.x - tickOuter, center.y),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
                // East
                drawLine(
                    color = ZorbiPrimaryCyan.copy(alpha = 0.85f),
                    start = Offset(center.x + tickInner, center.y),
                    end = Offset(center.x + tickOuter, center.y),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )

                // Layer D: Core solid cyan dot
                drawCircle(
                    color = ZorbiPrimaryCyan,
                    radius = 5f,
                    center = center
                )

                // Layer E: Crisp pinpoint white core
                drawCircle(
                    color = Color.White,
                    radius = 2f,
                    center = center
                )
            }
        }

        // Overlay status text when no tracking is actively locked
        if (showStatusOverlay && handData.status != HandTrackingStatus.TRACKING && handData.status != HandTrackingStatus.HAND_DETECTED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x990A0E1A)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (handData.status == HandTrackingStatus.NOT_CONNECTED) Icons.Default.Info else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (handData.status == HandTrackingStatus.NOT_CONNECTED) ZorbiError else ZorbiWarning,
                        modifier = Modifier.size(24.dp)
                    )

                    Text(
                        text = when (handData.status) {
                            HandTrackingStatus.NOT_CONNECTED -> "CAMERA / TRACKING NOT CONNECTED"
                            HandTrackingStatus.NO_HAND_DETECTED -> "NO HAND DETECTED • ALIGN HAND IN FRONT OF CAMERA"
                            else -> "ALIGN HAND IN FRONT OF CAMERA"
                        },
                        style = ZorbiMetricTypography.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (handData.status == HandTrackingStatus.NOT_CONNECTED) ZorbiError else ZorbiTextMuted
                        )
                    )
                }
            }
        }
    }
}
