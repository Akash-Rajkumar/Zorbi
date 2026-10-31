package com.zorbi.gesture

import com.zorbi.core.Calibration
import com.zorbi.core.GameProfile
import com.zorbi.core.GestureEvent
import com.zorbi.core.PoseFrame
import com.zorbi.core.Pt
import com.zorbi.core.Steer
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Interprets upper-body posture from [PoseFrame] into continuous [Steer] gesture events based on the shoulder line.
 *
 * Implements [com.zorbi.core.GestureInterpreter] for [TrackerId.POSE_LITE].
 *
 * Geometry & Conventions (FROZEN per Master Prompt v1.1 Section 3.3, 5.1):
 * - Image-Space Coordinates: Origin top-left, x and y normalized [0..1], rotated upright and mirrored
 *   for front camera (user's right shoulder appears on the right side of the frame, higher x).
 * - Shoulder Tracking: Uses MediaPipe Pose landmark indices 11 (LEFT_SHOULDER) and 12 (RIGHT_SHOULDER).
 * - Angle Computation:
 *   angleDeg = atan2(shoulderR.y - shoulderL.y, shoulderR.x - shoulderL.x) * (180 / PI)
 *   Clockwise (lean right): shoulderR moves down (dy > 0), producing positive angle.
 *   Counter-clockwise (lean left): shoulderR moves up (dy < 0), producing negative angle.
 * - Calibration:
 *   calibratedAngle = angleDeg - cal.neutralAngleDeg
 * - Deadzone & Scaling (Gate T-P2-7/8):
 *   If |calibratedAngle| <= profile.params.deadzoneDeg, value = 0.0f
 *   Else value = (calibratedAngle / cal.maxAngleDeg).coerceIn(-1.0f, 1.0f)
 * - Safety & Tracking Loss:
 *   Rejects frames with score < minConfidence, missing shoulder landmarks, invalid/non-finite coordinates,
 *   or stale/out-of-order timestamps.
 *   On tracking loss, immediately emits neutral Steer(value = 0.0f, angleDeg = 0.0f) to release active touches.
 */
class LeanGestureInterpreter(
    override val accepts: TrackerId = TrackerId.POSE_LITE,
    private val minConfidence: Float = 0.5f,
    private val minShoulderDistance: Float = 0.04f,
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
        if (frame !is PoseFrame) {
            return handleTrackingLoss(frame.tsNanos)
        }

        // Monotonic timestamp check
        if (lastFrameTsNanos > 0L && frame.tsNanos <= lastFrameTsNanos) {
            return emptyList()
        }

        // Temporal gap detection (> maxTrackingGapMs)
        if (lastFrameTsNanos > 0L && (frame.tsNanos - lastFrameTsNanos) > maxTrackingGapMs * 1_000_000L) {
            reset()
        }
        lastFrameTsNanos = frame.tsNanos

        // Confidence validation
        if (frame.score < minConfidence) {
            return handleTrackingLoss(frame.tsNanos)
        }

        // Landmark count validation (MediaPipe Pose Lite has 33 landmarks; shoulders are indices 11 and 12)
        if (frame.landmarks.size < 13) {
            return handleTrackingLoss(frame.tsNanos)
        }

        val lm11 = frame.landmarks[11]
        val lm12 = frame.landmarks[12]

        // Coordinate sanity check (non-NaN, finite)
        if (!lm11.x.isFinite() || !lm11.y.isFinite() || !lm12.x.isFinite() || !lm12.y.isFinite()) {
            return handleTrackingLoss(frame.tsNanos)
        }

        // Identify Left Shoulder vs Right Shoulder
        // In mirrored upright front camera view, user's right side appears on right of frame (larger x).
        val (shoulderL, shoulderR) = if (lm11.x <= lm12.x) {
            lm11 to lm12
        } else {
            lm12 to lm11
        }

        val dx = shoulderR.x - shoulderL.x
        val dy = shoulderR.y - shoulderL.y
        val distSq = dx * dx + dy * dy

        // If shoulders are unnaturally close or overlapping, reject as invalid
        if (distSq < minShoulderDistance * minShoulderDistance) {
            return handleTrackingLoss(frame.tsNanos)
        }

        // Calculate raw lean angle in degrees
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

