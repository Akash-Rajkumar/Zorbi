package com.zorbi.calib.fist

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PauseToggle
import com.zorbi.core.Pt
import com.zorbi.core.Side
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [FistDetector] geometry and temporal behavior (Person 1 / P1-8 / Gate T-P1-8).
 */
class FistDetectorTest {

    private lateinit var detector: FistDetector

    @Before
    fun setUp() {
        detector = FistDetector(
            holdDurationNanos = 1_000_000_000L, // 1.0 second hold threshold
            maxTrackingGapNanos = 350_000_000L  // 350 ms max frame gap
        )
    }

    /**
     * Synthesizes 21 landmarks for an open hand with extended fingers pointing upward.
     */
    private fun createOpenHand(score: Float = 0.95f): Hand {
        val lms = ArrayList<Pt>(21)
        val wrist = Pt(0.50f, 0.70f, 0.0f) // 0
        lms.add(wrist)

        // Thumb: 1 (CMC), 2 (MCP), 3 (IP), 4 (TIP) - abducted outwards
        lms.add(Pt(0.42f, 0.65f, 0.01f))
        lms.add(Pt(0.36f, 0.58f, 0.02f))
        lms.add(Pt(0.30f, 0.52f, 0.02f))
        lms.add(Pt(0.24f, 0.46f, 0.03f))

        // Index: 5 (MCP), 6 (PIP), 7 (DIP), 8 (TIP) - fully extended upwards
        lms.add(Pt(0.42f, 0.50f, 0.01f))
        lms.add(Pt(0.41f, 0.38f, 0.01f))
        lms.add(Pt(0.40f, 0.28f, 0.01f))
        lms.add(Pt(0.40f, 0.18f, 0.01f))

        // Middle: 9 (MCP), 10 (PIP), 11 (DIP), 12 (TIP) - fully extended upwards
        lms.add(Pt(0.50f, 0.48f, 0.00f)) // palmScale = dist(0, 9) = 0.22
        lms.add(Pt(0.50f, 0.35f, 0.00f))
        lms.add(Pt(0.50f, 0.24f, 0.00f))
        lms.add(Pt(0.50f, 0.14f, 0.00f))

        // Ring: 13 (MCP), 14 (PIP), 15 (DIP), 16 (TIP) - fully extended upwards
        lms.add(Pt(0.58f, 0.50f, -0.01f))
        lms.add(Pt(0.59f, 0.38f, -0.01f))
        lms.add(Pt(0.60f, 0.28f, -0.01f))
        lms.add(Pt(0.60f, 0.19f, -0.01f))

        // Pinky: 17 (MCP), 18 (PIP), 19 (DIP), 20 (TIP) - fully extended upwards
        lms.add(Pt(0.65f, 0.54f, -0.02f))
        lms.add(Pt(0.67f, 0.44f, -0.02f))
        lms.add(Pt(0.68f, 0.36f, -0.02f))
        lms.add(Pt(0.69f, 0.28f, -0.02f))

        return Hand(Side.RIGHT, score, lms)
    }

    /**
     * Synthesizes 21 landmarks for a tightly closed fist with fingers curled into the palm.
     */
    private fun createClosedFist(score: Float = 0.95f): Hand {
        val lms = ArrayList<Pt>(21)
        val wrist = Pt(0.50f, 0.70f, 0.0f) // 0
        lms.add(wrist)

        // Thumb: curled across the folded fingers (near index/middle MCP 5 and 9)
        lms.add(Pt(0.44f, 0.65f, 0.01f))
        lms.add(Pt(0.42f, 0.58f, 0.03f))
        lms.add(Pt(0.45f, 0.52f, 0.04f))
        lms.add(Pt(0.48f, 0.48f, 0.04f)) // TIP close to palm/MCP 9

        // Index: 5 (MCP), 6 (PIP), 7 (DIP), 8 (TIP) - curled inward
        lms.add(Pt(0.42f, 0.50f, 0.01f))
        lms.add(Pt(0.41f, 0.42f, 0.03f)) // PIP knuckles reach up to y=0.42
        lms.add(Pt(0.43f, 0.46f, 0.01f)) // DIP curls back down
        lms.add(Pt(0.44f, 0.50f, 0.00f)) // TIP folded down to palm/MCP

        // Middle: 9 (MCP), 10 (PIP), 11 (DIP), 12 (TIP) - curled inward
        lms.add(Pt(0.50f, 0.48f, 0.00f))
        lms.add(Pt(0.50f, 0.40f, 0.02f))
        lms.add(Pt(0.51f, 0.44f, 0.01f))
        lms.add(Pt(0.51f, 0.48f, 0.00f))

        // Ring: 13 (MCP), 14 (PIP), 15 (DIP), 16 (TIP) - curled inward
        lms.add(Pt(0.58f, 0.50f, -0.01f))
        lms.add(Pt(0.58f, 0.42f, 0.01f))
        lms.add(Pt(0.57f, 0.46f, 0.00f))
        lms.add(Pt(0.57f, 0.50f, -0.01f))

        // Pinky: 17 (MCP), 18 (PIP), 19 (DIP), 20 (TIP) - curled inward
        lms.add(Pt(0.65f, 0.54f, -0.02f))
        lms.add(Pt(0.65f, 0.46f, 0.00f))
        lms.add(Pt(0.64f, 0.50f, -0.01f))
        lms.add(Pt(0.63f, 0.54f, -0.02f))

        return Hand(Side.RIGHT, score, lms)
    }

    // 1. Open hand never triggers pause
    @Test
    fun openHand_neverTriggersPause() {
        val openHand = createOpenHand()
        assertThat(detector.isFist(openHand)).isFalse()

        // Stream open hand frames for 5 seconds at 30 fps (150 frames)
        val events = mutableListOf<com.zorbi.core.GestureEvent>()
        for (i in 0 until 150) {
            val tsNanos = 1_000_000_000L + i * 33_333_333L
            events.addAll(detector.onHand(openHand, tsNanos))
        }

        assertThat(events).isEmpty()
        assertThat(detector.state).isEqualTo(FistDetector.State.OPEN)
        assertThat(detector.holdProgress).isEqualTo(0.0f)
    }

    // 2. Fist held for ~1 second triggers exactly one PauseToggle
    @Test
    fun fistHeldOneSecond_triggersExactlyOnePauseToggle() {
        val fist = createClosedFist()
        assertThat(detector.isFist(fist)).isTrue()

        val events = mutableListOf<com.zorbi.core.GestureEvent>()

        // 31 frames at 33.3ms intervals = 1000ms
        for (i in 0..30) {
            val tsNanos = 1_000_000_000L + i * 33_333_334L
            events.addAll(detector.onHand(fist, tsNanos))
        }

        assertThat(events).hasSize(1)
        val event = events[0]
        assertThat(event).isInstanceOf(PauseToggle::class.java)
        assertThat(detector.state).isEqualTo(FistDetector.State.TRIGGERED)
        assertThat(detector.holdProgress).isEqualTo(1.0f)
    }

    // 3. Fist held for several seconds produces exactly one event
    @Test
    fun fistHeldSeveralSeconds_producesExactlyOneEvent() {
        val fist = createClosedFist()
        val events = mutableListOf<com.zorbi.core.GestureEvent>()

        // Hold fist for 5 full seconds at 30 fps (150 frames)
        for (i in 0 until 150) {
            val tsNanos = 1_000_000_000L + i * 33_333_333L
            events.addAll(detector.onHand(fist, tsNanos))
        }

        // Exactly one event emitted when 1-second threshold crossed, never repeated
        assertThat(events).hasSize(1)
        assertThat(detector.state).isEqualTo(FistDetector.State.TRIGGERED)
    }

    // 4. Brief fist-like movement does not trigger
    @Test
    fun briefFistMovement_doesNotTrigger() {
        val fist = createClosedFist()
        val openHand = createOpenHand()
        val events = mutableListOf<com.zorbi.core.GestureEvent>()

        // Fist closed for only 400ms (12 frames)
        for (i in 0..12) {
            val tsNanos = 1_000_000_000L + i * 33_333_333L
            events.addAll(detector.onHand(fist, tsNanos))
        }

        assertThat(events).isEmpty()
        assertThat(detector.state).isEqualTo(FistDetector.State.HOLDING)
        assertThat(detector.holdProgress).isLessThan(0.5f)

        // Hand opens back up at 500ms
        events.addAll(detector.onHand(openHand, 1_500_000_000L))

        assertThat(events).isEmpty()
        assertThat(detector.state).isEqualTo(FistDetector.State.OPEN)
        assertThat(detector.holdProgress).isEqualTo(0.0f)
    }

    // 5. Tracking dropout resets hold and does not trigger false pause
    @Test
    fun trackingDropout_resetsHoldTimer() {
        val fist = createClosedFist()
        val events = mutableListOf<com.zorbi.core.GestureEvent>()

        // Hold fist for 700ms
        for (i in 0..21) {
            val tsNanos = 1_000_000_000L + i * 33_333_333L
            events.addAll(detector.onHand(fist, tsNanos))
        }
        assertThat(events).isEmpty()
        assertThat(detector.state).isEqualTo(FistDetector.State.HOLDING)

        // Tracking dropout: 500ms frame gap (> maxTrackingGapNanos = 350ms)
        val dropoutTs = 1_000_000_000L + 21 * 33_333_333L + 500_000_000L
        events.addAll(detector.onHand(null, dropoutTs))

        assertThat(events).isEmpty()
        assertThat(detector.holdProgress).isEqualTo(0.0f)

        // Hand reappears after dropout for another 400ms: should NOT combine to trigger
        for (i in 1..12) {
            val tsNanos = dropoutTs + i * 33_333_333L
            events.addAll(detector.onHand(fist, tsNanos))
        }

        // Still no trigger because hold was reset by tracking dropout
        assertThat(events).isEmpty()
    }

    // 6. Invalid landmarks and low confidence are handled safely
    @Test
    fun invalidLandmarksAndLowConfidence_handledSafely() {
        val fist = createClosedFist()

        // Low confidence hand (< 0.50f)
        val lowConfHand = Hand(Side.RIGHT, 0.40f, fist.landmarks)
        assertThat(detector.isFist(lowConfHand)).isFalse()
        assertThat(detector.onHand(lowConfHand, 1_000_000_000L)).isEmpty()

        // Missing landmarks (< 21 points)
        val missingLmHand = Hand(Side.RIGHT, 0.90f, fist.landmarks.take(15))
        assertThat(detector.isFist(missingLmHand)).isFalse()
        assertThat(detector.onHand(missingLmHand, 2_000_000_000L)).isEmpty()

        // NaN coordinates
        val nanLms = fist.landmarks.toMutableList()
        nanLms[8] = Pt(Float.NaN, 0.5f)
        val nanHand = Hand(Side.RIGHT, 0.90f, nanLms)
        assertThat(detector.isFist(nanHand)).isFalse()
        assertThat(detector.onHand(nanHand, 3_000_000_000L)).isEmpty()

        assertThat(detector.state).isEqualTo(FistDetector.State.NO_HAND)
    }

    // 7. Opening and closing the hand again permits a second event
    @Test
    fun openingAndClosingHand_permitsSecondPauseToggle() {
        val fist = createClosedFist()
        val openHand = createOpenHand()
        val events = mutableListOf<com.zorbi.core.GestureEvent>()

        var ts = 1_000_000_000L

        // First 1-second fist hold -> 1 event (35 frames at 33.33ms = ~1.17s)
        for (i in 0..34) {
            events.addAll(detector.onHand(fist, ts))
            ts += 33_333_334L
        }
        assertThat(events).hasSize(1)

        // Keep held for 1 second -> 0 additional events
        for (i in 0..34) {
            events.addAll(detector.onHand(fist, ts))
            ts += 33_333_334L
        }
        assertThat(events).hasSize(1)

        // Hand opens for 1 second
        for (i in 0..34) {
            events.addAll(detector.onHand(openHand, ts))
            ts += 33_333_334L
        }
        assertThat(events).hasSize(1)
        assertThat(detector.state).isEqualTo(FistDetector.State.OPEN)

        // Fist closed again and held for 1 second -> exactly 2nd event
        for (i in 0..34) {
            events.addAll(detector.onHand(fist, ts))
            ts += 33_333_334L
        }
        assertThat(events).hasSize(2)
        assertThat(events[0]).isInstanceOf(PauseToggle::class.java)
        assertThat(events[1]).isInstanceOf(PauseToggle::class.java)
    }

    // 8. Timestamp ordering and repeated frames are handled safely
    @Test
    fun timestampOrderingAndRepeatedFrames_handledSafely() {
        val fist = createClosedFist()

        detector.onHand(fist, 10_000_000L)

        // Out-of-order frame (earlier timestamp) -> safely ignored
        val outOfOrderEvents = detector.onHand(fist, 5_000_000L)
        assertThat(outOfOrderEvents).isEmpty()

        // Repeated timestamp -> safely ignored
        val repeatedEvents = detector.onHand(fist, 10_000_000L)
        assertThat(repeatedEvents).isEmpty()
    }

    // 9. No false triggers during 60 seconds of normal play (acceptance criteria)
    @Test
    fun sixtySecondsOfNormalPlay_zeroFalsePauseTriggers() {
        val openHand = createOpenHand()
        val events = mutableListOf<com.zorbi.core.GestureEvent>()

        // 60 seconds at 30 fps = 1,800 frames
        val startNanos = 10_000_000_000L
        for (frameIdx in 0 until 1800) {
            val tsNanos = startNanos + frameIdx * 33_333_333L

            // Simulate slight natural hand movement / finger sway in open hand
            val swayX = 0.01f * kotlin.math.sin(frameIdx * 0.1).toFloat()
            val swayY = 0.01f * kotlin.math.cos(frameIdx * 0.1).toFloat()
            val swayedLms = openHand.landmarks.map { Pt(it.x + swayX, it.y + swayY, it.z) }
            val swayedHand = Hand(openHand.side, openHand.score, swayedLms)

            events.addAll(detector.onHand(swayedHand, tsNanos))
        }

        // Must produce ZERO false triggers across all 1800 frames of 60-second play
        assertThat(events).isEmpty()
        assertThat(detector.state).isEqualTo(FistDetector.State.OPEN)
    }
}
