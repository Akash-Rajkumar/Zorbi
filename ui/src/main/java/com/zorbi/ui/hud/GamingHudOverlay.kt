package com.zorbi.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
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
import com.zorbi.ui.theme.ZorbiPrimaryCyan

/**
 * Master In-Game HUD Overlay Component.
 * Implements all specifications from Master Prompt Section 13:
 * - Minimal corner chip (FPS, latency)
 * - Neon hand/pose skeleton at reduced opacity (user toggle)
 * - Gesture trails and ripples for slashes and swipes
 * - Steering gauge for wheel and pose-lean
 * - Fist-pause badge
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
                        HandsFrame(
                            tsNanos = frame.tsNanos,
                            hands = listOf(
                                com.zorbi.core.Hand(
                                    side = com.zorbi.core.Side.RIGHT,
                                    score = 0.95f,
                                    landmarks = frame.landmarks
                                )
                            )
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

        // 4. Center Top Pause Indicator Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            PauseIndicatorBadge(isPaused = isFistPause)
        }

        // 5. Steering Gauge (Positioned safely above bottom 20% control boundary)
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
