package com.zorbi.gesture

import com.zorbi.core.Calibration
import com.zorbi.core.Dir
import com.zorbi.core.GameProfile
import com.zorbi.core.GestureEvent
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PauseToggle
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.Steer
import com.zorbi.core.Swipe
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.core.ZorbiGestureConfig
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Interprets dual-hand wrist & forward reach positions from [HandsFrame] into continuous [Steer] gesture events.
 *
 * Implements [com.zorbi.core.GestureInterpreter] for [TrackerId.HAND_DUAL_WHEEL].
 *
 * High-Accuracy & Low-Latency Features:
 * - Holding Steering: When hands are held in the steering wheel plane, tilt angle smoothly rotates the steering wheel.
 * - Changing Hand Position for Pedals:
 *   - Right hand reaches forward towards camera: GAS (+1.0f). Pull back: Coast.
 *   - Left hand reaches forward towards camera: BRAKE (-1.0f). Pull back: Coast.
 * - User Customizable Actions:
 *   - Pinch: Headlight toggle or Pause toggle (via [ZorbiGestureConfig.pinchAction]).
 *   - Fist Hold (>=700ms): Horn honk, Pause, Nitro Boost, or Handbrake (via [ZorbiGestureConfig.fistAction]).
 * - Right hand flick up/down: Gear Shifting (Drive / Reverse).
 */
class WheelGestureInterpreter(
    override val accepts: TrackerId = TrackerId.HAND_DUAL_WHEEL,
    private val minConfidence: Float = 0.30f,
    private val minWristDistance: Float = 0.04f,
    private val maxTrackingGapMs: Long = 500L
) : ResettableInterpreter {

    private var lastFrameTsNanos: Long = 0L
    private var wasTracking: Boolean = false
    private var lastEmittedValue: Float = 0.0f
    private var lastGearShiftMs: Long = 0L
    private var lastPinchTsNanos: Long = 0L
    private var fistHoldStartNanos: Long = 0L
    private var lastFistActionNanos: Long = 0L

    override fun reset() {
        lastFrameTsNanos = 0L
        wasTracking = false
        lastEmittedValue = 0.0f
        lastGearShiftMs = 0L
        lastPinchTsNanos = 0L
        fistHoldStartNanos = 0L
        lastFistActionNanos = 0L
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
            return emptyList()
        }

        // Temporal gap detection (> maxTrackingGapMs)
        if (lastFrameTsNanos > 0L && (frame.tsNanos - lastFrameTsNanos) > maxTrackingGapMs * 1_000_000L) {
            reset()
        }
        lastFrameTsNanos = frame.tsNanos

        // Normalize hand list (handle both separate Hand objects and concatenated 42-point Hands)
        val allHands = if (frame.hands.size == 1 && frame.hands[0].landmarks.size >= 42) {
            listOf(
                Hand(side = Side.LEFT, score = frame.hands[0].score, landmarks = frame.hands[0].landmarks.subList(0, 21)),
                Hand(side = Side.RIGHT, score = frame.hands[0].score, landmarks = frame.hands[0].landmarks.subList(21, 42))
            )
        } else {
            frame.hands
        }

        // Support Single Hand Mode when configured
        if (allHands.size == 1 && ZorbiGestureConfig.handMode == ZorbiGestureConfig.HandMode.SINGLE_HAND) {
            val hand = allHands[0]
            if (hand.score >= minConfidence && hand.landmarks.size >= 21) {
                return processSingleHand(hand, frame.tsNanos, profile, cal)
            }
        }

        // Require at least 2 hands for dual-hand steering wheel
        if (allHands.size < 2) {
            return handleTrackingLoss(frame.tsNanos)
        }

        val hand0 = allHands[0]
        val hand1 = allHands[1]

        // Confidence validation
        if (hand0.score < minConfidence || hand1.score < minConfidence) {
            return handleTrackingLoss(frame.tsNanos)
        }

        if (hand0.landmarks.size < 21 || hand1.landmarks.size < 21) {
            return handleTrackingLoss(frame.tsNanos)
        }

        // Identify Left Hand vs Right Hand strictly by screen X to eliminate mirror-reversal flicker
        val (handL, handR) = if (hand0.landmarks[0].x <= hand1.landmarks[0].x) hand0 to hand1 else hand1 to hand0

        val wristL = handL.landmarks[0]
        val wristR = handR.landmarks[0]

        if (!wristL.x.isFinite() || !wristL.y.isFinite() || !wristR.x.isFinite() || !wristR.y.isFinite()) {
            return handleTrackingLoss(frame.tsNanos)
        }

        val dx = wristR.x - wristL.x
        val dy = wristR.y - wristL.y
        val distSq = dx * dx + dy * dy

        if (distSq < minWristDistance * minWristDistance) {
            return handleTrackingLoss(frame.tsNanos)
        }

        val events = mutableListOf<GestureEvent>()

        // 1. Check Pinch Gesture (Thumb + Index touching)
        val isPinch = isPinching(handL) || isPinching(handR)
        if (isPinch) {
            if (frame.tsNanos - lastPinchTsNanos > 750_000_000L) {
                lastPinchTsNanos = frame.tsNanos
                if (ZorbiGestureConfig.pinchAction == ZorbiGestureConfig.PinchAction.PAUSE) {
                    events.add(PauseToggle(tsNanos = frame.tsNanos))
                } else {
                    // Speed 77.0f signals custom Pinch Action (Headlights, Wipers, Hazard)
                    events.add(Swipe(tsNanos = frame.tsNanos, dir = Dir.RIGHT, speed = 77.0f))
                }
            }
        }

        // 2. Check Fist Gesture (Closed fist held for >= 700ms)
        val isFist = isClosedFist(handL) || isClosedFist(handR)
        if (isFist) {
            if (fistHoldStartNanos == 0L) {
                fistHoldStartNanos = frame.tsNanos
            } else if (frame.tsNanos - fistHoldStartNanos >= 700_000_000L) {
                if (frame.tsNanos - lastFistActionNanos >= 1_200_000_000L) {
                    lastFistActionNanos = frame.tsNanos
                    if (ZorbiGestureConfig.fistAction == ZorbiGestureConfig.FistAction.PAUSE) {
                        events.add(PauseToggle(tsNanos = frame.tsNanos))
                    } else {
                        // Speed 88.0f signals custom Fist Action (Horn, Nitro Boost, Handbrake)
                        events.add(Swipe(tsNanos = frame.tsNanos, dir = Dir.LEFT, speed = 88.0f))
                    }
                }
            }
        } else {
            fistHoldStartNanos = 0L
        }

        // 3. Check vertical flick on Right Hand for Gear Changing (Drive / Reverse)
        val nowMs = frame.tsNanos / 1_000_000L
        if (nowMs - lastGearShiftMs > 600L) {
            if (wristR.y < 0.22f) {
                lastGearShiftMs = nowMs
                events.add(Swipe(tsNanos = frame.tsNanos, dir = Dir.UP, speed = 0.5f))
            } else if (wristR.y > 0.78f) {
                lastGearShiftMs = nowMs
                events.add(Swipe(tsNanos = frame.tsNanos, dir = Dir.DOWN, speed = 0.5f))
            }
        }

        // 4. Compute pure wheel angle in degrees
        val rawAngleRad = atan2(dy.toDouble(), dx.toDouble())
        val rawAngleDeg = Math.toDegrees(rawAngleRad).toFloat()
        val calibratedAngle = rawAngleDeg - cal.neutralAngleDeg

        // Deadzone and scaling (Gate T-P2-7/8)
        val deadzone = abs(profile.params.deadzoneDeg)
        val maxAngle = if (cal.maxAngleDeg > 0f) cal.maxAngleDeg else 35.0f

        val steerAngleRatio = if (abs(calibratedAngle) <= deadzone) {
            0.0f
        } else {
            (calibratedAngle / maxAngle).coerceIn(-1.0f, 1.0f)
        }

        // 5. Compute forward reach (Hand position changed for gas / brake pedals)
        // Measured purely along depth (z) and 2D landmark span, independent of steering tilt dy
        val tipL = handL.landmarks[12]
        val tipR = handR.landmarks[12]
        val sizeL = sqrt((tipL.x - wristL.x) * (tipL.x - wristL.x) + (tipL.y - wristL.y) * (tipL.y - wristL.y))
        val sizeR = sqrt((tipR.x - wristR.x) * (tipR.x - wristR.x) + (tipR.y - wristR.y) * (tipR.y - wristR.y))

        // When right hand pushes forward towards camera, wristR.z drops and sizeR expands
        val forwardReachR = (wristL.z - wristR.z) * 1.8f + (sizeR - sizeL) * 2.8f
        val forwardReachL = (wristR.z - wristL.z) * 1.8f + (sizeL - sizeR) * 2.8f

        val pedalThreshold = 0.14f
        val isGasForward = forwardReachR > pedalThreshold
        val isBrakeForward = forwardReachL > pedalThreshold

        // If hand position changed to reach forward, activate pedal; otherwise steer by wheel tilt
        val steerValue = when {
            isGasForward -> 1.0f
            isBrakeForward -> -1.0f
            else -> steerAngleRatio
        }

        wasTracking = true
        lastEmittedValue = steerValue

        events.add(
            Steer(
                tsNanos = frame.tsNanos,
                value = steerValue,
                angleDeg = calibratedAngle
            )
        )
        return events
    }

    private fun isPinching(hand: Hand): Boolean {
        if (hand.landmarks.size < 21) return false
        val thumbTip = hand.landmarks[4]
        val indexTip = hand.landmarks[8]
        val wrist = hand.landmarks[0]
        val middleMcp = hand.landmarks[9]

        val palmScale = sqrt(
            (wrist.x - middleMcp.x) * (wrist.x - middleMcp.x) +
            (wrist.y - middleMcp.y) * (wrist.y - middleMcp.y)
        ).coerceAtLeast(0.02f)

        val pinchDist = sqrt(
            (thumbTip.x - indexTip.x) * (thumbTip.x - indexTip.x) +
            (thumbTip.y - indexTip.y) * (thumbTip.y - indexTip.y)
        )

        val pinchRatio = pinchDist / palmScale
        return pinchRatio < 0.28f
    }

    private fun isClosedFist(hand: Hand): Boolean {
        if (hand.landmarks.size < 21) return false
        val wrist = hand.landmarks[0]
        val middleMcp = hand.landmarks[9]
        val palmDist = sqrt(
            (wrist.x - middleMcp.x) * (wrist.x - middleMcp.x) +
            (wrist.y - middleMcp.y) * (wrist.y - middleMcp.y)
        ).coerceAtLeast(0.02f)

        val indexTip = hand.landmarks[8]
        val middleTip = hand.landmarks[12]
        val ringTip = hand.landmarks[16]
        val pinkyTip = hand.landmarks[20]

        val indexMcp = hand.landmarks[5]
        val ringMcp = hand.landmarks[13]
        val pinkyMcp = hand.landmarks[17]

        fun d2w(p: Pt) = sqrt((p.x - wrist.x) * (p.x - wrist.x) + (p.y - wrist.y) * (p.y - wrist.y))

        return d2w(indexTip) < d2w(indexMcp) + 0.05f * palmDist &&
               d2w(middleTip) < d2w(middleMcp) + 0.05f * palmDist &&
               d2w(ringTip) < d2w(ringMcp) + 0.05f * palmDist &&
               d2w(pinkyTip) < d2w(pinkyMcp) + 0.05f * palmDist
    }

    private fun processSingleHand(
        hand: Hand,
        tsNanos: Long,
        profile: GameProfile,
        cal: Calibration
    ): List<GestureEvent> {
        val wrist = hand.landmarks[0]
        val knuckle = hand.landmarks[9] // Middle MCP
        val tip = hand.landmarks[12]   // Middle tip

        val events = mutableListOf<GestureEvent>()

        // 1. Check Pinch Gesture
        if (isPinching(hand)) {
            if (tsNanos - lastPinchTsNanos > 750_000_000L) {
                lastPinchTsNanos = tsNanos
                if (ZorbiGestureConfig.pinchAction == ZorbiGestureConfig.PinchAction.PAUSE) {
                    events.add(PauseToggle(tsNanos = tsNanos))
                } else {
                    events.add(Swipe(tsNanos = tsNanos, dir = Dir.RIGHT, speed = 77.0f))
                }
            }
        }

        // 2. Check Fist Gesture
        if (isClosedFist(hand)) {
            if (fistHoldStartNanos == 0L) {
                fistHoldStartNanos = tsNanos
            } else if (tsNanos - fistHoldStartNanos >= 700_000_000L) {
                if (tsNanos - lastFistActionNanos >= 1_200_000_000L) {
                    lastFistActionNanos = tsNanos
                    if (ZorbiGestureConfig.fistAction == ZorbiGestureConfig.FistAction.PAUSE) {
                        events.add(PauseToggle(tsNanos = tsNanos))
                    } else {
                        events.add(Swipe(tsNanos = tsNanos, dir = Dir.LEFT, speed = 88.0f))
                    }
                }
            }
        } else {
            fistHoldStartNanos = 0L
        }

        // 3. Compute Hand Tilt Angle (dx, -dy) from wrist to knuckle
        val dx = knuckle.x - wrist.x
        val dy = knuckle.y - wrist.y
        val rawTiltRad = atan2(dx.toDouble(), -dy.toDouble())
        val rawTiltDeg = Math.toDegrees(rawTiltRad).toFloat()
        val calibratedAngle = rawTiltDeg - cal.neutralAngleDeg

        val deadzone = abs(profile.params.deadzoneDeg)
        val maxAngle = if (cal.maxAngleDeg > 0f) cal.maxAngleDeg else 30.0f

        val steerRatio = if (abs(calibratedAngle) <= deadzone) {
            0.0f
        } else {
            (calibratedAngle / maxAngle).coerceIn(-1.0f, 1.0f)
        }

        // Forward reach (Hand depth / size) for Gas / Brake
        val handSpan = sqrt((tip.x - wrist.x) * (tip.x - wrist.x) + (tip.y - wrist.y) * (tip.y - wrist.y))
        val isGasForward = (wrist.z < -0.06f || handSpan > 0.35f)
        val isBrakeBack = (wrist.z > 0.08f || handSpan < 0.16f)

        val steerValue = when {
            isGasForward -> 1.0f
            isBrakeBack -> -1.0f
            else -> steerRatio
        }

        wasTracking = true
        lastEmittedValue = steerValue

        events.add(
            Steer(
                tsNanos = tsNanos,
                value = steerValue,
                angleDeg = calibratedAngle
            )
        )
        return events
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
