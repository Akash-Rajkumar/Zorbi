package com.zorbi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.HandsFrame
import com.zorbi.core.HudFrame
import com.zorbi.core.PoseFrame
import com.zorbi.core.Pt
import com.zorbi.core.TrackerFrame
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiCardShape
import com.zorbi.ui.theme.ZorbiError
import com.zorbi.ui.theme.ZorbiGlowCyan
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiNeonMagenta
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiWarning

/**
 * Standard tracking status enumeration for live vision indicators.
 */
enum class HandTrackingStatus(val label: String, val level: StatusLevel) {
    TRACKING("TRACKING", StatusLevel.SUCCESS),
    HAND_DETECTED("HAND DETECTED", StatusLevel.NEUTRAL),
    NO_HAND_DETECTED("NO HAND DETECTED", StatusLevel.WARNING),
    NOT_CONNECTED("CAMERA/TRACKING NOT CONNECTED", StatusLevel.ERROR)
}

/**
 * A historical point in the fading movement trail.
 */
data class FingertipTrailPoint(
    val normalizedOffset: Offset,
    val timestampNanos: Long = System.nanoTime()
)

/**
 * Extracted tracking state decoupled from specific frame formats.
 */
data class ExtractedHandData(
    val status: HandTrackingStatus,
    val fingertipPt: Pt?,
    val confidence: Float?,
    val allLandmarks: List<Pt>,
    val isPose: Boolean = false
)

/**
 * Pure extraction logic for converting TrackerFrame / HudFrame into fingertip and status data.
 * Adheres to Master Prompt Section 5.1 (Landmark 8 = INDEX_FINGER_TIP; upright and mirrored).
 */
object HandTrackingExtractor {

    fun extractFromTrackerFrame(
        frame: TrackerFrame?,
        isTrackerConnected: Boolean = true
    ): ExtractedHandData {
        if (!isTrackerConnected || frame == null) {
            return ExtractedHandData(
                status = HandTrackingStatus.NOT_CONNECTED,
                fingertipPt = null,
                confidence = null,
                allLandmarks = emptyList()
            )
        }

        return when (frame) {
            is HandsFrame -> {
                if (frame.hands.isEmpty()) {
                    ExtractedHandData(
                        status = HandTrackingStatus.NO_HAND_DETECTED,
                        fingertipPt = null,
                        confidence = null,
                        allLandmarks = emptyList()
                    )
                } else {
                    val hand = frame.hands.first()
                    // Landmark index 8 is canonically MediaPipe's INDEX_FINGER_TIP
                    val fingertip = hand.landmarks.getOrNull(8)
                    val status = if (fingertip != null) HandTrackingStatus.TRACKING else HandTrackingStatus.HAND_DETECTED
                    ExtractedHandData(
                        status = status,
                        fingertipPt = fingertip,
                        confidence = hand.score.takeIf { it > 0f },
                        allLandmarks = hand.landmarks
                    )
                }
            }
            is PoseFrame -> {
                if (frame.landmarks.isEmpty() || frame.score < 0.25f) {
                    ExtractedHandData(
                        status = HandTrackingStatus.NO_HAND_DETECTED,
                        fingertipPt = null,
                        confidence = null,
                        allLandmarks = emptyList()
                    )
                } else {
                    // Landmark 20 is RIGHT_INDEX, 19 is LEFT_INDEX in MediaPipe Pose
                    val fingertip = frame.landmarks.getOrNull(20) ?: frame.landmarks.getOrNull(19)
                    ExtractedHandData(
                        status = if (fingertip != null) HandTrackingStatus.TRACKING else HandTrackingStatus.HAND_DETECTED,
                        fingertipPt = fingertip,
                        confidence = frame.score.takeIf { it > 0f },
                        allLandmarks = frame.landmarks,
                        isPose = true
                    )
                }
            }
            else -> {
                ExtractedHandData(
                    status = HandTrackingStatus.NO_HAND_DETECTED,
                    fingertipPt = null,
                    confidence = null,
                    allLandmarks = emptyList()
                )
            }
        }
    }

    fun extractFromHudFrame(
        frame: HudFrame?,
        isEngineRunning: Boolean = true
    ): ExtractedHandData {
        if (!isEngineRunning || frame == null) {
            return ExtractedHandData(
                status = HandTrackingStatus.NOT_CONNECTED,
                fingertipPt = null,
                confidence = null,
                allLandmarks = emptyList()
            )
        }

        if (frame.landmarks.isEmpty()) {
            return ExtractedHandData(
                status = HandTrackingStatus.NO_HAND_DETECTED,
                fingertipPt = null,
                confidence = null,
                allLandmarks = emptyList()
            )
        }

        val fingertip = frame.landmarks.getOrNull(8)
        val status = if (fingertip != null) HandTrackingStatus.TRACKING else HandTrackingStatus.HAND_DETECTED
        return ExtractedHandData(
            status = status,
            fingertipPt = fingertip,
            confidence = null,
            allLandmarks = frame.landmarks
        )
    }

    fun pruneTrail(
        trail: MutableList<FingertipTrailPoint>,
        nowNanos: Long = System.nanoTime(),
        maxAgeNanos: Long = 400_000_000L // 400ms trail decay
    ) {
        trail.removeAll { nowNanos - it.timestampNanos > maxAgeNanos }
    }
}

/**
 * Compact tracking status pill for overlays and headers.
 */
@Composable
fun HandTrackingStatusChip(
    status: HandTrackingStatus,
    confidence: Float? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        color = ZorbiSurfaceElevated.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (status) {
                HandTrackingStatus.TRACKING -> ZorbiSuccess.copy(alpha = 0.5f)
                HandTrackingStatus.HAND_DETECTED -> ZorbiPrimaryCyan.copy(alpha = 0.5f)
                HandTrackingStatus.NO_HAND_DETECTED -> ZorbiWarning.copy(alpha = 0.4f)
                HandTrackingStatus.NOT_CONNECTED -> ZorbiError.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ZorbiStatusDot(
                level = status.level,
                animatePulse = status == HandTrackingStatus.TRACKING
            )

            Text(
                text = status.label,
                style = ZorbiMetricTypography.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (status) {
                        HandTrackingStatus.TRACKING -> ZorbiSuccess
                        HandTrackingStatus.HAND_DETECTED -> ZorbiPrimaryCyan
                        HandTrackingStatus.NO_HAND_DETECTED -> ZorbiWarning
                        HandTrackingStatus.NOT_CONNECTED -> ZorbiTextMuted
                    }
                )
            )

            if (confidence != null && confidence > 0f) {
                Spacer(modifier = Modifier.width(2.dp))
                val confPercent = (confidence * 100).toInt().coerceIn(0, 100)
                Text(
                    text = "$confPercent%",
                    style = ZorbiMetricTypography.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZorbiPrimaryCyan
                    )
                )
            }
        }
    }
}
