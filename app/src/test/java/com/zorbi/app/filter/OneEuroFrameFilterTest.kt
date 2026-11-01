package com.zorbi.app.filter

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PoseFrame
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.TrackerFrame
import org.junit.Test

/**
 * Unit tests for [OneEuroFrameFilter] adapter (Gate I-2 / Phase 2).
 */
class OneEuroFrameFilterTest {

    @Test
    fun filter_handsFrame_singleHand_preservesMetadataAndFiltersPoints() {
        val filter = OneEuroFrameFilter(minCutoff = 1.0f, beta = 0.0f)

        val rawPoints1 = listOf(Pt(0.50f, 0.50f, 0f), Pt(0.20f, 0.30f, 0f))
        val frame1 = HandsFrame(
            tsNanos = 1_000_000_000L,
            hands = listOf(Hand(side = Side.RIGHT, score = 0.95f, landmarks = rawPoints1))
        )

        // First frame: returns identical coordinates (initialized)
        val filtered1 = filter.filter(frame1) as HandsFrame
        assertThat(filtered1.tsNanos).isEqualTo(1_000_000_000L)
        assertThat(filtered1.hands).hasSize(1)
        assertThat(filtered1.hands[0].side).isEqualTo(Side.RIGHT)
        assertThat(filtered1.hands[0].score).isEqualTo(0.95f)
        assertThat(filtered1.hands[0].landmarks[0].x).isWithin(1e-5f).of(0.50f)

        // Second frame with jump (jitter)
        val rawPoints2 = listOf(Pt(0.60f, 0.50f, 0f), Pt(0.20f, 0.30f, 0f))
        val frame2 = HandsFrame(
            tsNanos = 1_050_000_000L, // 50ms later
            hands = listOf(Hand(side = Side.RIGHT, score = 0.95f, landmarks = rawPoints2))
        )

        val filtered2 = filter.filter(frame2) as HandsFrame
        assertThat(filtered2.tsNanos).isEqualTo(1_050_000_000L)
        // Value must be smoothed between 0.50 and 0.60
        val smoothedX = filtered2.hands[0].landmarks[0].x
        assertThat(smoothedX).isGreaterThan(0.50f)
        assertThat(smoothedX).isLessThan(0.60f)
    }

    @Test
    fun filter_handsFrame_dualHands_filtersIndependentlyWithoutCrosstalk() {
        val filter = OneEuroFrameFilter(minCutoff = 1.0f, beta = 0.0f)

        val leftLms = listOf(Pt(0.20f, 0.50f, 0f))
        val rightLms = listOf(Pt(0.80f, 0.50f, 0f))

        val frame1 = HandsFrame(
            tsNanos = 1_000_000_000L,
            hands = listOf(
                Hand(side = Side.LEFT, score = 0.90f, landmarks = leftLms),
                Hand(side = Side.RIGHT, score = 0.92f, landmarks = rightLms)
            )
        )

        val filtered1 = filter.filter(frame1) as HandsFrame
        assertThat(filtered1.hands).hasSize(2)
        assertThat(filtered1.hands[0].landmarks[0].x).isWithin(1e-5f).of(0.20f)
        assertThat(filtered1.hands[1].landmarks[0].x).isWithin(1e-5f).of(0.80f)

        // Move only right hand
        val frame2 = HandsFrame(
            tsNanos = 1_050_000_000L,
            hands = listOf(
                Hand(side = Side.LEFT, score = 0.90f, landmarks = leftLms), // unchanged
                Hand(side = Side.RIGHT, score = 0.92f, landmarks = listOf(Pt(0.90f, 0.50f, 0f))) // moved
            )
        )

        val filtered2 = filter.filter(frame2) as HandsFrame
        // Left hand must remain ~0.20f
        assertThat(filtered2.hands[0].landmarks[0].x).isWithin(1e-4f).of(0.20f)
        // Right hand must be smoothed between 0.80 and 0.90
        val rightX = filtered2.hands[1].landmarks[0].x
        assertThat(rightX).isGreaterThan(0.80f)
        assertThat(rightX).isLessThan(0.90f)
    }

    @Test
    fun filter_poseFrame_preservesMetadataAndSmoothsPoints() {
        val filter = OneEuroFrameFilter(minCutoff = 1.0f, beta = 0.0f)

        val posePoints1 = List(33) { Pt(0.50f, 0.50f, 0f) }
        val frame1 = PoseFrame(
            tsNanos = 2_000_000_000L,
            score = 0.88f,
            landmarks = posePoints1
        )

        val filtered1 = filter.filter(frame1) as PoseFrame
        assertThat(filtered1.tsNanos).isEqualTo(2_000_000_000L)
        assertThat(filtered1.score).isEqualTo(0.88f)
        assertThat(filtered1.landmarks).hasSize(33)
        assertThat(filtered1.landmarks[11].x).isWithin(1e-5f).of(0.50f)

        // Move left shoulder (index 11)
        val posePoints2 = posePoints1.toMutableList().apply {
            this[11] = Pt(0.70f, 0.50f, 0f)
        }
        val frame2 = PoseFrame(
            tsNanos = 2_050_000_000L,
            score = 0.88f,
            landmarks = posePoints2
        )

        val filtered2 = filter.filter(frame2) as PoseFrame
        val smoothedShoulderX = filtered2.landmarks[11].x
        assertThat(smoothedShoulderX).isGreaterThan(0.50f)
        assertThat(smoothedShoulderX).isLessThan(0.70f)
    }

    @Test
    fun filter_emptyHandsFrame_returnsUnmodified() {
        val filter = OneEuroFrameFilter()
        val emptyFrame = HandsFrame(tsNanos = 3_000_000_000L, hands = emptyList())

        val result = filter.filter(emptyFrame)
        assertThat(result).isSameInstanceAs(emptyFrame)
    }

    @Test
    fun reset_clearsInternalSmoothingHistory() {
        val filter = OneEuroFrameFilter(minCutoff = 1.0f, beta = 0.0f)

        val frame1 = HandsFrame(
            tsNanos = 1_000_000_000L,
            hands = listOf(Hand(side = Side.RIGHT, score = 1.0f, landmarks = listOf(Pt(0.50f, 0.50f, 0f))))
        )
        filter.filter(frame1)

        // Reset
        filter.reset()

        // Next frame at completely new timestamp and location should treat as fresh initial sample
        val frame2 = HandsFrame(
            tsNanos = 5_000_000_000L,
            hands = listOf(Hand(side = Side.RIGHT, score = 1.0f, landmarks = listOf(Pt(0.80f, 0.80f, 0f))))
        )
        val filtered2 = filter.filter(frame2) as HandsFrame
        // Because it was reset, first sample is returned as-is
        assertThat(filtered2.hands[0].landmarks[0].x).isWithin(1e-5f).of(0.80f)
    }
}
