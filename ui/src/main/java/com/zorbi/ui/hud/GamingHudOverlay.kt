package com.zorbi.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.HandsFrame
import com.zorbi.core.HudFrame
import com.zorbi.core.PauseToggle
import com.zorbi.core.PoseFrame
import com.zorbi.core.Slash
import com.zorbi.core.Steer
import com.zorbi.core.Swipe
import com.zorbi.core.TrackerId
import com.zorbi.ui.components.ZorbiBadge
import com.zorbi.ui.components.ZorbiFpsChip
import com.zorbi.ui.screens.calibrator.SkeletonCanvas
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiPrimaryCyan

/**
 * Master In-Game HUD Overlay Component.
 * Implements all specifications from Master Prompt Section 13:
 * - Minimal corner chip (FPS, latency)
 * - Neon hand/pose skeleton at reduced opacity (user toggle)
 * - Gesture trails and ripples for slashes and swipes
 * - Steering gauge for wheel and pose-lean
 * - Fist-pause badge
 * - Real-time gesture status indicator banner (verifying gestures are working)
 * - Non-occluding layout respecting 20% screen edge bounds
 */
@Composable
fun GamingHudOverlay(
    frame: HudFrame?,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    initialOpacity: HudOpacity = HudOpacity.SUBTLE,
    onOpacityChange: ((HudOpacity) -> Unit)? = null
) {
    var opacity by remember { mutableStateOf(initialOpacity) }

    // Steer angle & value extracted from latest HudFrame
    val steerEvent = frame?.lastGesture as? Steer
    val steerValue = steerEvent?.value ?: 0f
    val steerAngle = steerEvent?.angleDeg ?: 0f

    // Active gesture visual effect
    val activeVisual = remember(frame?.lastGesture?.tsNanos) {
        val gesture = frame?.lastGesture
        if (gesture is Slash || gesture is Swipe) {
            ActiveGestureVisual(gesture)
        } else {
            null
        }
    }

    // Determine if pause was triggered via closed fist gesture
    val isFistPause = isPaused || (frame?.lastGesture is PauseToggle)

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Neon Skeleton Layer (drawn with active opacity toggle)
        if (opacity != HudOpacity.HIDDEN && frame != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(opacity.alpha)
            ) {
                // Synthesize frame for canvas rendering
                val trackerFrame = when (frame.trackerId) {
                    TrackerId.HAND_SINGLE, TrackerId.HAND_DUAL_WHEEL -> {
                        val hands = if (frame.landmarks.size >= 42) {
                            listOf(
                                com.zorbi.core.Hand(
                                    side = com.zorbi.core.Side.LEFT,
                                    score = 0.95f,
                                    landmarks = frame.landmarks.subList(0, 21)
                                ),
                                com.zorbi.core.Hand(
                                    side = com.zorbi.core.Side.RIGHT,
                                    score = 0.95f,
                                    landmarks = frame.landmarks.subList(21, 42)
                                )
                            )
                        } else if (frame.landmarks.isNotEmpty()) {
                            listOf(
                                com.zorbi.core.Hand(
                                    side = com.zorbi.core.Side.RIGHT,
                                    score = 0.95f,
                                    landmarks = frame.landmarks.take(21)
                                )
                            )
                        } else {
                            emptyList()
                        }
                        HandsFrame(
                            tsNanos = frame.tsNanos,
                            hands = hands
                        )
                    }
                    TrackerId.POSE_LITE -> {
                        PoseFrame(
                            tsNanos = frame.tsNanos,
                            score = 0.90f,
                            landmarks = frame.landmarks
                        )
                    }
                }

                SkeletonCanvas(
                    frame = trackerFrame,
                    isTrackingActive = frame.landmarks.isNotEmpty(),
                    showFingertipMarker = false,
                    showFadingTrail = true,
                    showDebugBones = (opacity == HudOpacity.FULL),
                    showStatusOverlay = false,
                    isLandscapeMode = (frame.trackerId == TrackerId.HAND_DUAL_WHEEL),
                    activeGestureMode = if (frame.trackerId == TrackerId.HAND_DUAL_WHEEL) "STEER" else "SWIPE",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. Dynamic Gesture Ripples & Laser Slash Trails
        GestureRippleOverlay(
            activeVisual = activeVisual,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Top-Right Corner Diagnostic Cluster (Respecting edge bounds)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live FPS & Latency chip from real HudFrame metrics (or [NOT CONNECTED] when idle)
            val isLiveSignal = frame != null && frame.fps > 0f
            ZorbiFpsChip(
                fps = frame?.fps ?: 0f,
                latencyMs = frame?.e2eLatencyMs ?: 0f,
                isSimulated = !isLiveSignal
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Opacity toggle chip (Cycle: 100% -> 40% -> 15% -> Off)
            Box(
                modifier = Modifier.clickable {
                    val next = opacity.next()
                    opacity = next
                    onOpacityChange?.invoke(next)
                }
            ) {
                ZorbiBadge(
                    text = "HUD ${opacity.label}",
                    accentColor = ZorbiAccentViolet
                )
            }
        }

        // 4. In-Game Live Gesture Feedback Banner (Tells user if gestures are working in real-time)
        val nowNanos = remember(frame?.tsNanos) { System.nanoTime() }
        val lastEvent = frame?.lastGesture
        val lastGestureAgeNanos = if (lastEvent != null) {
            (nowNanos - lastEvent.tsNanos).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }
        val isRecentGesture = lastGestureAgeNanos < 1_500_000_000L // 1.5 seconds visibility

        val (gestureBannerText, gestureBannerColor, isActionPulsing) = when {
            isRecentGesture && lastEvent is PauseToggle -> {
                Triple("🤏 PINCH / FIST: PAUSE TOGGLED", Color(0xFFFFB703), true)
            }
            isRecentGesture && lastEvent is Steer -> {
                when {
                    lastEvent.value >= 0.95f -> Triple("🏎️ GAS ACTIVE • RIGHT HAND FORWARD", Color(0xFF00FF88), true)
                    lastEvent.value <= -0.95f -> Triple("🛑 BRAKE ACTIVE • LEFT HAND FORWARD", Color(0xFFFF3366), true)
                    kotlin.math.abs(lastEvent.angleDeg) > 6f -> Triple("🔄 STEER: ${lastEvent.angleDeg.toInt()}° TILT", Color(0xFF00E5FF), true)
                    else -> Triple("🎮 HOLDING STEERING (Coasting)", Color(0xFF00E5FF), false)
                }
            }
            isRecentGesture && lastEvent is Swipe -> {
                when (lastEvent.speed) {
                    88.0f -> Triple("📢 FIST: HORN HONKED!", Color(0xFFFFB703), true)
                    77.0f -> Triple("💡 PINCH: HEADLIGHTS TOGGLED!", Color(0xFF00E5FF), true)
                    else -> {
                        val dirSymbol = when (lastEvent.dir) {
                            com.zorbi.core.Dir.UP -> "⬆️ UP"
                            com.zorbi.core.Dir.DOWN -> "⬇️ DOWN"
                            com.zorbi.core.Dir.LEFT -> "⬅️ LEFT"
                            com.zorbi.core.Dir.RIGHT -> "➡️ RIGHT"
                        }
                        Triple("⚡ SWIPE $dirSymbol (${(lastEvent.speed * 100).toInt()}%)", Color(0xFFFFB703), true)
                    }
                }
            }
            isRecentGesture && lastEvent is Slash -> {
                Triple("⚔️ SLASH ACTIVE (${lastEvent.path.size} PTS)", Color(0xFFBD00FF), true)
            }
            frame != null && frame.landmarks.size >= 42 -> {
                Triple("🟢 BOTH HANDS DETECTED (L + R) • READY TO DRIVE", Color(0xFF00FF88), false)
            }
            frame != null && frame.trackerId == TrackerId.HAND_DUAL_WHEEL && frame.landmarks.isNotEmpty() -> {
                val isLeft = frame.landmarks[0].x < 0.50f
                if (isLeft) {
                    Triple("🟡 LEFT HAND DETECTED ✋ • RAISE RIGHT HAND", Color(0xFFFFB703), false)
                } else {
                    Triple("🟡 RIGHT HAND DETECTED ✋ • RAISE LEFT HAND", Color(0xFFFFB703), false)
                }
            }
            frame != null && frame.landmarks.isNotEmpty() -> {
                val isLeft = frame.landmarks[0].x < 0.50f
                val sideName = if (isLeft) "LEFT" else "RIGHT"
                Triple("🟢 $sideName HAND ACTIVE ✋ • MOTION READY", Color(0xFF00E5FF), false)
            }
            else -> {
                Triple("🔴 NO HANDS DETECTED • SHOW HANDS TO CAMERA", Color(0xFFFF2255), false)
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xF20A0E1A),
            border = androidx.compose.foundation.BorderStroke(
                width = if (isActionPulsing) 2.dp else 1.dp,
                color = gestureBannerColor
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(gestureBannerColor, CircleShape)
                )
                Text(
                    text = gestureBannerText,
                    style = ZorbiMetricTypography.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = gestureBannerColor
                    )
                )
            }
        }

        // 5. Center Top Pause Indicator Badge
        if (isFistPause) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                PauseIndicatorBadge(isPaused = true)
            }
        }

        // 6. Steering Gauge (Positioned safely above bottom 20% control boundary)
        if (frame?.trackerId == TrackerId.HAND_DUAL_WHEEL || frame?.trackerId == TrackerId.POSE_LITE) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
            ) {
                SteeringGauge(
                    steerValue = steerValue,
                    angleDeg = steerAngle
                )
            }
        }
    }
}
