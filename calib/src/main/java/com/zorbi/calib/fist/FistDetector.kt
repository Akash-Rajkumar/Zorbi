package com.zorbi.calib.fist

import com.zorbi.core.GestureEvent
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PauseToggle
import com.zorbi.core.Pt
import com.zorbi.core.TrackerFrame
import kotlin.math.sqrt

/**
 * Geometric fist detection and fist-to-pause trigger (Person 1 / P1-8 / Gate T-P1-8).
 *
 * ## Detection Geometry:
 * - Detects closed fist from 21 MediaPipe hand landmarks using geometric joint relationships.
 * - Normalized against hand size using palm scale $D_{\text{palm}} = \operatorname{dist3D}(lm[0], lm[9])$
 *   (wrist to middle finger MCP) so detection is invariant to camera distance.
 * - All 4 fingers (Index, Middle, Ring, Pinky) must be curled inward:
 *   - $\text{curlRatio} = \frac{\operatorname{dist}(\text{TIP}, \text{MCP})}{\operatorname{dist}(\text{TIP}, \text{PIP}) + \operatorname{dist}(\text{PIP}, \text{MCP})} < 0.68$.
 *   - $\operatorname{dist}(\text{TIP}, \text{MCP}) < 0.80 \times D_{\text{palm}}$.
 *   - $\operatorname{dist}(\text{TIP}, \text{Wrist}) < \operatorname{dist}(\text{PIP}, \text{Wrist}) + 0.05 \times D_{\text{palm}}$.
 * - Thumb tip must be folded against or close to the palm / index/middle MCP ($< 0.90 \times D_{\text{palm}}$).
 * - Zero additional ML models; pure landmark geometry.
 *
 * ## Temporal Behavior:
 * - Fist must be continuously held for approximately 1 second ([holdDurationNanos]).
 * - Emits exactly ONE [PauseToggle] when the 1-second threshold is reached.
 * - Does not emit repeated events while the fist remains held.
 * - Requires the hand to open again before another fist gesture can trigger.
 * - Resets on tracking dropout ($\Delta t >$ [maxTrackingGapNanos] or invalid landmarks).
 */
class FistDetector(
    val holdDurationNanos: Long = DEFAULT_HOLD_DURATION_NANOS,
    val maxTrackingGapNanos: Long = DEFAULT_MAX_TRACKING_GAP_NANOS
) {
    companion object {
        const val DEFAULT_HOLD_DURATION_NANOS = 1_000_000_000L // 1.0 second (~1000 ms)
        const val DEFAULT_MAX_TRACKING_GAP_NANOS = 350_000_000L // 350 ms

        const val MIN_PALM_SCALE = 0.02f
        const val MIN_HAND_CONFIDENCE = 0.50f
        const val REQUIRED_LANDMARK_COUNT = 21

        // Geometric thresholds
        const val MAX_CURL_RATIO = 0.68f
        const val MAX_TIP_TO_MCP_PALM_RATIO = 0.80f
        const val MAX_THUMB_TO_PALM_RATIO = 0.90f
        const val MAX_THUMB_TO_WRIST_RATIO = 1.30f
    }

    enum class State {
        NO_HAND,
        OPEN,
        HOLDING,
        TRIGGERED
    }

    private var _state: State = State.NO_HAND
    val state: State get() = _state

    private var fistStartNanos: Long? = null
    private var lastFrameNanos: Long? = null
    private var hasFiredForCurrentFist: Boolean = false

    /**
     * Hold progress towards the 1-second trigger: 0.0f (none) to 1.0f (fully held/triggered).
     */
    var holdProgress: Float = 0.0f
        private set

    /**
     * Evaluates a generic [TrackerFrame] and emits [PauseToggle] if a 1-second fist hold completes.
     */
    fun onFrame(frame: TrackerFrame): List<GestureEvent> {
        return if (frame is HandsFrame) {
            onHandsFrame(frame)
        } else {
            handleTrackingLost()
            emptyList()
        }
    }

    /**
     * Evaluates a [HandsFrame] and returns gesture events.
     */
    fun onHandsFrame(frame: HandsFrame): List<GestureEvent> {
        val validHand = frame.hands.firstOrNull { it.score >= MIN_HAND_CONFIDENCE }
        return onHand(validHand, frame.tsNanos)
    }

    /**
     * Evaluates an individual [Hand] at timestamp [tsNanos].
     */
    fun onHand(hand: Hand?, tsNanos: Long): List<GestureEvent> {
        val prevTs = lastFrameNanos

        // Guard against non-monotonic or repeated timestamps
        if (prevTs != null && tsNanos <= prevTs) {
            return emptyList()
        }

        // Check for tracking dropout gap
        if (prevTs != null && (tsNanos - prevTs) > maxTrackingGapNanos) {
            // Tracking gap exceeded: reset active hold
            fistStartNanos = null
            holdProgress = 0.0f
            if (_state == State.HOLDING) {
                _state = State.NO_HAND
            }
        }
        lastFrameNanos = tsNanos

        if (hand == null || !isValidHand(hand)) {
            handleTrackingLost()
            return emptyList()
        }

        val isClosed = isFist(hand)

        if (isClosed) {
            if (hasFiredForCurrentFist) {
                // Already fired for this fist hold; keep in TRIGGERED state, emit no further events
                _state = State.TRIGGERED
                holdProgress = 1.0f
                return emptyList()
            }

            val start = fistStartNanos
            if (start == null) {
                fistStartNanos = tsNanos
                _state = State.HOLDING
                holdProgress = 0.0f
                return emptyList()
            }

            val elapsedNanos = tsNanos - start
            val progress = (elapsedNanos.toFloat() / holdDurationNanos).coerceIn(0.0f, 1.0f)
            holdProgress = progress

            return if (elapsedNanos >= holdDurationNanos) {
                hasFiredForCurrentFist = true
                _state = State.TRIGGERED
                holdProgress = 1.0f
                listOf(PauseToggle(tsNanos))
            } else {
                _state = State.HOLDING
                emptyList()
            }
        } else {
            // Hand is open
            _state = State.OPEN
            fistStartNanos = null
            holdProgress = 0.0f
            hasFiredForCurrentFist = false // Re-arm when hand is confirmed open
            return emptyList()
        }
    }

    /**
     * Pure geometric classifier: determines whether [hand] is in a closed fist posture.
     */
    fun isFist(hand: Hand): Boolean {
        if (!isValidHand(hand)) return false
        val lm = hand.landmarks
        val wrist = lm[0]

        // Reference palm scale: distance between wrist (0) and middle finger MCP (9)
        val palmScale = dist3D(wrist, lm[9])
        if (palmScale < MIN_PALM_SCALE) return false

        // Check all 4 fingers (Index, Middle, Ring, Pinky) are curled inward
        // Format: (tipIndex, pipIndex, mcpIndex)
        val fingerIndices = listOf(
            Triple(8, 6, 5),    // Index
            Triple(12, 10, 9),  // Middle
            Triple(16, 14, 13), // Ring
            Triple(20, 18, 17)  // Pinky
        )

        for ((tip, pip, mcp) in fingerIndices) {
            val dTipMcp = dist3D(lm[tip], lm[mcp])
            val dTipPip = dist3D(lm[tip], lm[pip])
            val dPipMcp = dist3D(lm[pip], lm[mcp])
            val segmentSum = dTipPip + dPipMcp

            if (segmentSum <= 1e-6f) return false

            val curlRatio = dTipMcp / segmentSum
            // Must be bent/curled
            if (curlRatio > MAX_CURL_RATIO) return false

            // Fingertip must be close to MCP relative to palm scale
            if (dTipMcp > MAX_TIP_TO_MCP_PALM_RATIO * palmScale) return false

            // Fingertip must not extend significantly farther from wrist than PIP joint
            val dTipWrist = dist3D(lm[tip], wrist)
            val dPipWrist = dist3D(lm[pip], wrist)
            if (dTipWrist > dPipWrist + 0.05f * palmScale) return false
        }

        // Thumb check: tip (4) must be curled near the palm / index-middle joints
        val thumbTip = lm[4]
        val dThumbToPalm = minOf(
            dist3D(thumbTip, lm[5]),  // Index MCP
            dist3D(thumbTip, lm[6]),  // Index PIP
            dist3D(thumbTip, lm[9]),  // Middle MCP
            dist3D(thumbTip, lm[10]), // Middle PIP
            dist3D(thumbTip, lm[2])   // Thumb MCP
        )
        if (dThumbToPalm > MAX_THUMB_TO_PALM_RATIO * palmScale) return false

        // Thumb tip must not be extended far away from wrist
        val dThumbWrist = dist3D(thumbTip, wrist)
        if (dThumbWrist > MAX_THUMB_TO_WRIST_RATIO * palmScale) return false

        return true
    }

    private fun handleTrackingLost() {
        fistStartNanos = null
        holdProgress = 0.0f
        if (_state != State.TRIGGERED) {
            _state = State.NO_HAND
        }
    }

    private fun isValidHand(hand: Hand): Boolean {
        if (hand.score < MIN_HAND_CONFIDENCE) return false
        if (hand.landmarks.size != REQUIRED_LANDMARK_COUNT) return false
        for (i in 0 until REQUIRED_LANDMARK_COUNT) {
            val p = hand.landmarks[i]
            if (p.x.isNaN() || p.x.isInfinite() ||
                p.y.isNaN() || p.y.isInfinite() ||
                p.z.isNaN() || p.z.isInfinite()
            ) {
                return false
            }
        }
        return true
    }

    private fun dist3D(a: Pt, b: Pt): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Resets internal tracking and hold state.
     */
    fun reset() {
        _state = State.NO_HAND
        fistStartNanos = null
        lastFrameNanos = null
        hasFiredForCurrentFist = false
        holdProgress = 0.0f
    }
}
