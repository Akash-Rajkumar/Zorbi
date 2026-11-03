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
import androidx.compose.ui.graphics.drawscope.rotate
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
    showFingertipMarker: Boolean = false,
    showFadingTrail: Boolean = true,
    showDebugBones: Boolean = false,
    confidenceScore: Float? = null,
    showStatusOverlay: Boolean = true,
    isLandscapeMode: Boolean = false,
    activeGestureMode: String = "STEER",
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

            // 3D Perspective Projection helper based on landmark depth z
            fun Pt.to3DOffset(): Offset {
                val depthFactor = (1.0f - z * 1.5f).coerceIn(0.65f, 1.45f)
                val projX = (x - 0.5f) * depthFactor + 0.5f
                val projY = (y - 0.5f) * depthFactor + 0.5f
                return Offset(projX * canvasWidth, projY * canvasHeight)
            }

            // 1. Extract and Parse Detected Hands (chunking dual-hand landmarks)
            val parsedHandList = mutableListOf<List<Pt>>()
            when (frame) {
                is HandsFrame -> {
                    for (hand in frame.hands) {
                        if (hand.landmarks.size >= 42) {
                            parsedHandList.add(hand.landmarks.subList(0, 21))
                            parsedHandList.add(hand.landmarks.subList(21, 42))
                        } else if (hand.landmarks.size >= 21) {
                            parsedHandList.add(hand.landmarks.take(21))
                        }
                    }
                }
                is PoseFrame -> {}
                null -> {}
            }

            // Real-time dynamic steering angle & forward pedal depression detection
            var currentSteerAngleDeg = 0f
            var isGasActive = false
            var isBrakeActive = false

            if (parsedHandList.size >= 2) {
                val h0 = parsedHandList[0]
                val h1 = parsedHandList[1]
                val (leftHand, rightHand) = if (h0[0].x <= h1[0].x) h0 to h1 else h1 to h0
                val dx = rightHand[0].x - leftHand[0].x
                val dy = rightHand[0].y - leftHand[0].y
                if (kotlin.math.abs(dx) > 0.04f) {
                    currentSteerAngleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                }

                val tipL = leftHand[12]
                val tipR = rightHand[12]
                val sizeL = kotlin.math.sqrt((tipL.x - leftHand[0].x) * (tipL.x - leftHand[0].x) + (tipL.y - leftHand[0].y) * (tipL.y - leftHand[0].y))
                val sizeR = kotlin.math.sqrt((tipR.x - rightHand[0].x) * (tipR.x - rightHand[0].x) + (tipR.y - rightHand[0].y) * (tipR.y - rightHand[0].y))

                // Pure forward reach differential along depth and span (independent of steering tilt dy)
                val reachR = (leftHand[0].z - rightHand[0].z) * 1.8f + (sizeR - sizeL) * 2.8f
                val reachL = (rightHand[0].z - leftHand[0].z) * 1.8f + (sizeL - sizeR) * 2.8f
                if (reachR > 0.14f) {
                    isGasActive = true
                }
                if (reachL > 0.14f) {
                    isBrakeActive = true
                }
            } else if (parsedHandList.size == 1) {
                val single = parsedHandList[0]
                val wrist = single[0]
                val tip = single[12]
                val size = kotlin.math.sqrt((tip.x - wrist.x) * (tip.x - wrist.x) + (tip.y - wrist.y) * (tip.y - wrist.y))
                val isForward = size > 0.18f || wrist.z < -0.02f
                if (wrist.x > 0.5f && isForward) isGasActive = true
                if (wrist.x <= 0.5f && isForward) isBrakeActive = true
            }

            // Detect pinch gesture across hands
            var isPinchActive = false
            var pinchCenter: Offset? = null
            for (hand in parsedHandList) {
                if (hand.size >= 21) {
                    val thumbTip = hand[4]
                    val indexTip = hand[8]
                    val wrist = hand[0]
                    val middleMcp = hand[9]
                    val palmScale = kotlin.math.sqrt(
                        (wrist.x - middleMcp.x) * (wrist.x - middleMcp.x) +
                        (wrist.y - middleMcp.y) * (wrist.y - middleMcp.y)
                    ).coerceAtLeast(0.02f)
                    val pinchDist = kotlin.math.sqrt(
                        (thumbTip.x - indexTip.x) * (thumbTip.x - indexTip.x) +
                        (thumbTip.y - indexTip.y) * (thumbTip.y - indexTip.y)
                    )
                    if (pinchDist / palmScale < 0.28f) {
                        isPinchActive = true
                        pinchCenter = Offset(
                            (thumbTip.x + indexTip.x) * 0.5f * canvasWidth,
                            (thumbTip.y + indexTip.y) * 0.5f * canvasHeight
                        )
                    }
                }
            }

            // 3D Landscape Cockpit Perspective Environment
            if (isLandscapeMode) {
                // Horizon Glow
                drawLine(
                    color = ZorbiPrimaryCyan.copy(alpha = 0.25f),
                    start = Offset(0f, canvasHeight * 0.55f),
                    end = Offset(canvasWidth, canvasHeight * 0.55f),
                    strokeWidth = 2f
                )
                // Perspective Grid Lines
                for (step in 1..7) {
                    val xVanishing = canvasWidth * 0.5f
                    val yVanishing = canvasHeight * 0.55f
                    val xBottom = canvasWidth * (step / 8f)
                    drawLine(
                        color = ZorbiPrimaryCyan.copy(alpha = 0.12f),
                        start = Offset(xVanishing, yVanishing),
                        end = Offset(xBottom, canvasHeight),
                        strokeWidth = 1.2f
                    )
                }
            }

            // 2. Virtual Holographic 3D Steering Wheel (when in Steering mode or Landscape Driving)
            if (activeGestureMode == "STEER" || isLandscapeMode) {
                val wheelCenter = Offset(canvasWidth * 0.5f, canvasHeight * 0.52f)
                val wheelRadius = (canvasHeight * 0.30f).coerceIn(48f, 105f)

                // Rotate steering wheel smoothly based on real-time hand tilt angle
                rotate(
                    degrees = currentSteerAngleDeg.coerceIn(-65f, 65f),
                    pivot = wheelCenter
                ) {
                    // Outer Holographic Glowing Rim
                    drawCircle(
                        color = ZorbiGlowCyan.copy(alpha = 0.22f),
                        radius = wheelRadius,
                        center = wheelCenter,
                        style = Stroke(width = 10f)
                    )
                    drawCircle(
                        color = ZorbiPrimaryCyan.copy(alpha = 0.85f),
                        radius = wheelRadius,
                        center = wheelCenter,
                        style = Stroke(width = 4.5f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f),
                        radius = wheelRadius - 2f,
                        center = wheelCenter,
                        style = Stroke(width = 1.2f)
                    )

                    // Top 12 O'Clock Racing Alignment Stripe (Vibrant Neon Amber)
                    drawLine(
                        color = Color(0xFFFFB703),
                        start = Offset(wheelCenter.x, wheelCenter.y - wheelRadius - 8f),
                        end = Offset(wheelCenter.x, wheelCenter.y - wheelRadius + 8f),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )

                    // Left & Right Hand Grip Cushions (9-10 o'clock and 2-3 o'clock)
                    drawArc(
                        color = ZorbiAccentViolet.copy(alpha = 0.8f),
                        startAngle = 150f,
                        sweepAngle = 60f,
                        useCenter = false,
                        topLeft = Offset(wheelCenter.x - wheelRadius, wheelCenter.y - wheelRadius),
                        size = androidx.compose.ui.geometry.Size(wheelRadius * 2f, wheelRadius * 2f),
                        style = Stroke(width = 7.5f)
                    )
                    drawArc(
                        color = ZorbiAccentViolet.copy(alpha = 0.8f),
                        startAngle = -30f,
                        sweepAngle = 60f,
                        useCenter = false,
                        topLeft = Offset(wheelCenter.x - wheelRadius, wheelCenter.y - wheelRadius),
                        size = androidx.compose.ui.geometry.Size(wheelRadius * 2f, wheelRadius * 2f),
                        style = Stroke(width = 7.5f)
                    )

                    // 3 Cyber Spokes
                    // Left Spoke (to 9 o'clock)
                    drawLine(
                        color = ZorbiPrimaryCyan.copy(alpha = 0.9f),
                        start = wheelCenter,
                        end = Offset(wheelCenter.x - wheelRadius + 2f, wheelCenter.y),
                        strokeWidth = 3.5f,
                        cap = StrokeCap.Round
                    )
                    // Right Spoke (to 3 o'clock)
                    drawLine(
                        color = ZorbiPrimaryCyan.copy(alpha = 0.9f),
                        start = wheelCenter,
                        end = Offset(wheelCenter.x + wheelRadius - 2f, wheelCenter.y),
                        strokeWidth = 3.5f,
                        cap = StrokeCap.Round
                    )
                    // Bottom Spoke (to 6 o'clock)
                    drawLine(
                        color = ZorbiPrimaryCyan.copy(alpha = 0.9f),
                        start = wheelCenter,
                        end = Offset(wheelCenter.x, wheelCenter.y + wheelRadius - 2f),
                        strokeWidth = 3.5f,
                        cap = StrokeCap.Round
                    )

                    // Central ZORBI Emblem Hub
                    drawCircle(
                        color = Color(0xF00A0E1A),
                        radius = wheelRadius * 0.32f,
                        center = wheelCenter
                    )
                    drawCircle(
                        color = ZorbiPrimaryCyan,
                        radius = wheelRadius * 0.32f,
                        center = wheelCenter,
                        style = Stroke(width = 2.5f)
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 5f,
                        center = wheelCenter
                    )
                }

                // Digital Steer Angle HUD Readout
                val angleInt = currentSteerAngleDeg.toInt()
                val readoutBg = if (kotlin.math.abs(angleInt) > 5) Color(0xCC002612) else Color(0xCC0A0E1A)
                val readoutBorder = if (kotlin.math.abs(angleInt) > 5) Color(0xFF00FF88) else ZorbiPrimaryCyan.copy(alpha = 0.5f)

                drawRoundRect(
                    color = readoutBg,
                    topLeft = Offset(canvasWidth * 0.5f - 60f, wheelCenter.y - wheelRadius - 24f),
                    size = androidx.compose.ui.geometry.Size(120f, 18f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = readoutBorder,
                    topLeft = Offset(canvasWidth * 0.5f - 60f, wheelCenter.y - wheelRadius - 24f),
                    size = androidx.compose.ui.geometry.Size(120f, 18f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                    style = Stroke(width = 1.2f)
                )
            }

            // 3. Interactive Cockpit Pedal Pads (Brake on bottom-left, Gas on bottom-right)
            if (activeGestureMode == "STEER" || isLandscapeMode) {
                // BRAKE PAD (Bottom-Left)
                val brakeX = 18f
                val brakeY = canvasHeight - 54f
                val brakeW = 66f
                val brakeH = 44f
                val brakeOffset = if (isBrakeActive) 4f else 0f

                // Brake Pad Body
                drawRoundRect(
                    color = if (isBrakeActive) Color(0xCCFF0055) else Color(0x442B050E),
                    topLeft = Offset(brakeX, brakeY + brakeOffset),
                    size = androidx.compose.ui.geometry.Size(brakeW, brakeH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    color = if (isBrakeActive) Color(0xFFFF3366) else Color(0x88FF3366),
                    topLeft = Offset(brakeX, brakeY + brakeOffset),
                    size = androidx.compose.ui.geometry.Size(brakeW, brakeH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                    style = Stroke(width = if (isBrakeActive) 3f else 1.5f)
                )
                // Non-slip rubber ribbed grips across Brake pedal
                for (rib in 1..3) {
                    val ribY = brakeY + brakeOffset + rib * 10f
                    drawLine(
                        color = if (isBrakeActive) Color.White.copy(alpha = 0.9f) else Color(0x66FF3366),
                        start = Offset(brakeX + 8f, ribY),
                        end = Offset(brakeX + brakeW - 8f, ribY),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }

                // GAS / ACCELERATOR PAD (Bottom-Right, taller, contoured like sports car throttle)
                val gasX = canvasWidth - 68f
                val gasY = canvasHeight - 62f
                val gasW = 50f
                val gasH = 52f
                val gasOffset = if (isGasActive) 4f else 0f

                // Gas Pad Body
                drawRoundRect(
                    color = if (isGasActive) Color(0xCC00FF88) else Color(0x44002612),
                    topLeft = Offset(gasX, gasY + gasOffset),
                    size = androidx.compose.ui.geometry.Size(gasW, gasH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    color = if (isGasActive) Color(0xFF00FF88) else Color(0x8800FF88),
                    topLeft = Offset(gasX, gasY + gasOffset),
                    size = androidx.compose.ui.geometry.Size(gasW, gasH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                    style = Stroke(width = if (isGasActive) 3f else 1.5f)
                )
                // Non-slip throttle grip slots
                for (rib in 1..4) {
                    val ribY = gasY + gasOffset + rib * 10f
                    drawLine(
                        color = if (isGasActive) Color.White.copy(alpha = 0.9f) else Color(0x6600FF88),
                        start = Offset(gasX + 7f, ribY),
                        end = Offset(gasX + gasW - 7f, ribY),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Gear Shifter Column (for Bus Simulator Indonesia & Gear mode)
            if (isLandscapeMode || activeGestureMode == "GEAR") {
                val gearX = canvasWidth - 32f
                val gearY = 20f
                drawRect(
                    color = Color(0x660A0E1A),
                    topLeft = Offset(gearX - 12f, gearY),
                    size = androidx.compose.ui.geometry.Size(28f, 76f)
                )
                drawRect(
                    color = ZorbiPrimaryCyan.copy(alpha = 0.6f),
                    topLeft = Offset(gearX - 12f, gearY),
                    size = androidx.compose.ui.geometry.Size(28f, 76f),
                    style = Stroke(width = 1.5f)
                )
                // Gear slots [R (Reverse), N (Neutral), D (Drive)]
                drawCircle(color = Color(0xFFFF5555), radius = 4f, center = Offset(gearX + 2f, gearY + 14f))
                drawCircle(color = Color.White.copy(alpha = 0.7f), radius = 4f, center = Offset(gearX + 2f, gearY + 38f))
                drawCircle(color = Color(0xFF00FF88), radius = 5.5f, center = Offset(gearX + 2f, gearY + 62f))
            }

            // 3.5 Pinch-to-Pause Visual Feedback
            if (isPinchActive && pinchCenter != null) {
                drawCircle(
                    color = Color(0xFFFFB703).copy(alpha = 0.35f),
                    radius = 28f,
                    center = pinchCenter
                )
                drawCircle(
                    color = Color(0xFFFFB703),
                    radius = 16f,
                    center = pinchCenter,
                    style = Stroke(width = 2.5f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = pinchCenter
                )
            }

            // 4. Render 3D Hand Mesh & Volumetric Bones for ALL Detected Hands
            for (pts in parsedHandList) {
                if (pts.size >= 21) {
                    // 3D Palm Cyber Mesh (translucent holographic palm fill)
                    val palmPath = Path().apply {
                        val wrist = pts[0].to3DOffset()
                        moveTo(wrist.x, wrist.y)
                        val palmIndices = listOf(1, 2, 5, 9, 13, 17, 0)
                        for (idx in palmIndices) {
                            val o = pts[idx].to3DOffset()
                            lineTo(o.x, o.y)
                        }
                        close()
                    }
                    drawPath(
                        path = palmPath,
                        color = ZorbiPrimaryCyan.copy(alpha = 0.15f)
                    )
                    drawPath(
                        path = palmPath,
                        color = ZorbiGlowCyan.copy(alpha = 0.35f),
                        style = Stroke(width = 1.5f)
                    )

                    // 3D Depth-shaded Neon Bones
                    for ((startIdx, endIdx) in HAND_CONNECTIONS) {
                        val start = pts[startIdx].to3DOffset()
                        val end = pts[endIdx].to3DOffset()
                        val avgZ = (pts[startIdx].z + pts[endIdx].z) * 0.5f
                        val depthAlpha = (0.75f - avgZ * 1.2f).coerceIn(0.35f, 1.0f)
                        val boneWidth = (3.5f - avgZ * 4.0f).coerceIn(2.0f, 6.5f)

                        // Soft 3D atmospheric neon glow
                        drawLine(
                            color = ZorbiGlowCyan.copy(alpha = 0.35f * depthAlpha),
                            start = start,
                            end = end,
                            strokeWidth = boneWidth * 2.2f,
                            cap = StrokeCap.Round
                        )
                        // Solid core 3D laser bone
                        drawLine(
                            color = ZorbiPrimaryCyan.copy(alpha = depthAlpha),
                            start = start,
                            end = end,
                            strokeWidth = boneWidth,
                            cap = StrokeCap.Round
                        )
                    }

                    // 3D Spherical Joint Nodes with Specular Gleam
                    for ((index, pt) in pts.withIndex()) {
                        if (index == 8) continue // Strictly remove any dot/marker on index fingertip
                        val center = pt.to3DOffset()
                        val depthScale = (1.0f - pt.z * 1.5f).coerceIn(0.6f, 1.4f)
                        val baseRadius = if (index in listOf(4, 12, 16, 20)) 5.0f else 3.5f
                        val radius = baseRadius * depthScale

                        val jointColor = when (index) {
                            4, 12, 16, 20 -> ZorbiAccentViolet // Fingertips
                            0 -> ZorbiSuccess // Wrist anchor
                            else -> ZorbiPrimaryCyan.copy(alpha = 0.85f)
                        }

                        // Joint sphere base
                        drawCircle(
                            color = jointColor,
                            radius = radius,
                            center = center
                        )
                        // 3D Specular highlight for metallic depth
                        drawCircle(
                            color = Color.White.copy(alpha = 0.7f),
                            radius = radius * 0.35f,
                            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f)
                        )
                    }
                }
            }

            when (frame) {
                is HandsFrame -> {}
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
