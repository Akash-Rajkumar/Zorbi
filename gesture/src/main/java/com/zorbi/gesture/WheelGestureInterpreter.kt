package com.zorbi.gesture

import com.zorbi.core.Calibration
import com.zorbi.core.GameProfile
import com.zorbi.core.GestureEvent
import com.zorbi.core.HandsFrame
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.Steer
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Interprets dual-hand wrist positions from [HandsFrame] into continuous [Steer] gesture events.
 *
 * Implements [com.zorbi.core.GestureInterpreter] for [TrackerId.HAND_DUAL_WHEEL].
 *
 * Geometry & Conventions (FROZEN per Master Prompt v1.1 Section 3.3, 5.1):
 * - Image-Space Coordinates: Origin top-left, x and y normalized [0..1], rotated upright and mirrored
 *   for front camera (user's right hand appears on the right side of the frame, higher x).
 * - Wrist Tracking: Uses landmark index 0 (WRIST) of the left and right hands.
 * - Angle Computation:
 *   angleDeg = atan2(wristR.y - wristL.y, wristR.x - wristL.x) * (180 / PI)
 *   Clockwise (turn right): wristR moves down (dy > 0), producing positive angle.
 *   Counter-clockwise (turn left): wristR moves up (dy < 0), producing negative angle.
 * - Calibration:
 *   calibratedAngle = angleDeg - cal.neutralAngleDeg
 * - Deadzone & Scaling (Gate T-P2-7/8):
 *   If |calibratedAngle| <= profile.params.deadzoneDeg, value = 0.0f
 *   Else value = (calibratedAngle / cal.maxAngleDeg).coerceIn(-1.0f, 1.0f)
 * - Safety & Tracking Loss:
 *   Rejects frames with < 2 hands, score < minConfidence, missing wrist landmarks, invalid coordinates,
 *   or stale/out-of-order timestamps.
 *   On tracking loss, immediately emits neutral Steer(value = 0.0f, angleDeg = 0.0f) to release active touches.
 */
class WheelGestureInterpreter(
    override val accepts: TrackerId = TrackerId.HAND_DUAL_WHEEL,
    private val minConfidence: Float = 0.5f,
    private val minWristDistance: Float = 0.04f,
    private val maxTrackingGapMs: Long = 500L
) : ResettableInterpreter {

    private var lastFrameTsNanos: Long = 0L
    private var wasTracking: Boolean = false
    private var lastEmittedValue: Float = 0.0f

    override fun reset() {
        lastFrameTsNanos = 0L
        wasTracking = false
        lastEmittedValue = 0.0f
    }

    override fun onFrame(
        frame: TrackerFrame,
        profile: GameProfile,
        cal: Calibration
    ): List<GestureEvent> {
        if (frame !is HandsFrame) {
            return handleTrackingLoss(frame.tsNanos)
        }

        // Monotonic timestamp check
        if (lastFrameTsNanos > 0L && frame.tsNanos <= lastFrameTsNanos) {
            // Stale or out-of-order frame: reject
            return emptyList()
        }

        // Temporal gap detection (> maxTrackingGapMs)
        if (lastFrameTsNanos > 0L && (frame.tsNanos - lastFrameTsNanos) > maxTrackingGapMs * 1_000_000L) {
            reset()
        }
        lastFrameTsNanos = frame.tsNanos

        // Require at least 2 hands
        if (frame.hands.size < 2) {
            return handleTrackingLoss(frame.tsNanos)
        }

        val hand0 = frame.hands[0]
        val hand1 = frame.hands[1]

        // Confidence validation
        if (hand0.score < minConfidence || hand1.score < minConfidence) {
            return handleTrackingLoss(frame.tsNanos)
        }

        // Landmark count validation (WRIST is index 0; full MediaPipe hand has 21 points)
        if (hand0.landmarks.size < 21 || hand1.landmarks.size < 21) {
            return handleTrackingLoss(frame.tsNanos)
        }

        val lm0 = hand0.landmarks[0]
        val lm1 = hand1.landmarks[0]

        // Coordinate sanity check (non-NaN, finite)
        if (!lm0.x.isFinite() || !lm0.y.isFinite() || !lm1.x.isFinite() || !lm1.y.isFinite()) {
            return handleTrackingLoss(frame.tsNanos)
        }

        // Identify Left Wrist vs Right Wrist
        // In mirrored upright front camera view, user's right hand appears on right of frame (larger x).
        // If Side labels are distinct (LEFT vs RIGHT), respect them; otherwise sort by x coordinate.
        val (wristL, wristR) = if (hand0.side == Side.LEFT && hand1.side == Side.RIGHT) {
            lm0 to lm1
        } else if (hand0.side == Side.RIGHT && hand1.side == Side.LEFT) {
            lm1 to lm0
        } else {
            if (lm0.x <= lm1.x) lm0 to lm1 else lm1 to lm0
        }

        val dx = wristR.x - wristL.x
        val dy = wristR.y - wristL.y
        val distSq = dx * dx + dy * dy

        // If hands are unnaturally close or overlapping, reject as invalid
        if (distSq < minWristDistance * minWristDistance) {
            return handleTrackingLoss(frame.tsNanos)
        }

        // Calculate raw wheel angle in degrees
        val rawAngleRad = atan2(dy.toDouble(), dx.toDouble())
        val rawAngleDeg = Math.toDegrees(rawAngleRad).toFloat()

        // Apply neutral calibration offset
        val calibratedAngle = rawAngleDeg - cal.neutralAngleDeg

        // Deadzone and scaling (Gate T-P2-7/8)
        val deadzone = abs(profile.params.deadzoneDeg)
        val maxAngle = if (cal.maxAngleDeg > 0f) cal.maxAngleDeg else 35.0f

        val steerValue = if (abs(calibratedAngle) <= deadzone) {
            0.0f
        } else {
            (calibratedAngle / maxAngle).coerceIn(-1.0f, 1.0f)
        }

        wasTracking = true
        lastEmittedValue = steerValue

        return listOf(
            Steer(
                tsNanos = frame.tsNanos,
                value = steerValue,
                angleDeg = calibratedAngle
            )
        )
    }

    private fun handleTrackingLoss(tsNanos: Long): List<GestureEvent> {
        if (wasTracking || lastEmittedValue != 0.0f) {
            wasTracking = false
            lastEmittedValue = 0.0f
            return listOf(
                Steer(
                    tsNanos = tsNanos,
                    value = 0.0f,
                    angleDeg = 0.0f
                )
            )
        }
        return emptyList()
    }
}

