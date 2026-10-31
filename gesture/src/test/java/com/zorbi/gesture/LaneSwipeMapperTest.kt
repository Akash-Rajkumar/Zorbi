package com.zorbi.gesture

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Dir
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.Pt
import com.zorbi.core.Swipe
import com.zorbi.core.TargetPoint
import com.zorbi.core.TrackerId
import org.junit.Test

class LaneSwipeMapperTest {

    private val profileWithTargets = GameProfile(
        schemaVersion = 1,
        packageName = "com.subwaysurfers.runner",
        displayName = "Subway Surfers",
        orientation = ProfileOrientation.PORTRAIT,
        tracker = TrackerId.HAND_SINGLE,
        mapping = MappingType.LANE_SWIPE,
        targets = mapOf(
            "center" to TargetPoint(0.50f, 0.60f),
            "left" to TargetPoint(0.20f, 0.60f),
            "right" to TargetPoint(0.80f, 0.60f),
            "up" to TargetPoint(0.50f, 0.30f),
            "down" to TargetPoint(0.50f, 0.90f)
        ),
        params = ProfileParams()
    )

    private val profileWithoutTargets = GameProfile(
        schemaVersion = 1,
        packageName = "com.generic.runner",
        displayName = "Generic Runner",
        orientation = ProfileOrientation.PORTRAIT,
        tracker = TrackerId.HAND_SINGLE,
        mapping = MappingType.LANE_SWIPE,
        targets = emptyMap(),
        params = ProfileParams()
    )

    @Test
    fun testMap_leftSwipeWithExplicitTarget() {
        val swipe = Swipe(tsNanos = 1000L, dir = Dir.LEFT, speed = 2.0f)
        val touch = LaneSwipeMapper.map(swipe, profileWithTargets)

        assertThat(touch.from).isEqualTo(Pt(0.50f, 0.60f))
        assertThat(touch.to).isEqualTo(Pt(0.20f, 0.60f))
        assertThat(touch.durationMs).isEqualTo(100L)
    }

    @Test
    fun testMap_rightSwipeWithExplicitTarget() {
        val swipe = Swipe(tsNanos = 1000L, dir = Dir.RIGHT, speed = 2.0f)
        val touch = LaneSwipeMapper.map(swipe, profileWithTargets)

        assertThat(touch.from).isEqualTo(Pt(0.50f, 0.60f))
        assertThat(touch.to).isEqualTo(Pt(0.80f, 0.60f))
    }

    @Test
    fun testMap_upSwipeWithExplicitTarget() {
        val swipe = Swipe(tsNanos = 1000L, dir = Dir.UP, speed = 2.0f)
        val touch = LaneSwipeMapper.map(swipe, profileWithTargets)

        assertThat(touch.from).isEqualTo(Pt(0.50f, 0.60f))
        assertThat(touch.to).isEqualTo(Pt(0.50f, 0.30f))
    }

    @Test
    fun testMap_downSwipeWithExplicitTarget() {
        val swipe = Swipe(tsNanos = 1000L, dir = Dir.DOWN, speed = 2.0f)
        val touch = LaneSwipeMapper.map(swipe, profileWithTargets)

        assertThat(touch.from).isEqualTo(Pt(0.50f, 0.60f))
        assertThat(touch.to).isEqualTo(Pt(0.50f, 0.90f))
    }

    @Test
    fun testMap_fallbackWhenTargetsAreEmpty() {
        val swipeLeft = Swipe(tsNanos = 1000L, dir = Dir.LEFT, speed = 2.0f)
        val touchLeft = LaneSwipeMapper.map(swipeLeft, profileWithoutTargets)

        // Default center is (0.5, 0.5)
        assertThat(touchLeft.from.x).isWithin(1e-5f).of(0.5f)
        assertThat(touchLeft.from.y).isWithin(1e-5f).of(0.5f)
        // Default left offset is -0.3f -> 0.2f
        assertThat(touchLeft.to.x).isWithin(1e-5f).of(0.2f)
        assertThat(touchLeft.to.y).isWithin(1e-5f).of(0.5f)

        val swipeRight = Swipe(tsNanos = 1000L, dir = Dir.RIGHT, speed = 2.0f)
        val touchRight = LaneSwipeMapper.map(swipeRight, profileWithoutTargets)
        assertThat(touchRight.to.x).isWithin(1e-5f).of(0.8f)
        assertThat(touchRight.to.y).isWithin(1e-5f).of(0.5f)

        val swipeUp = Swipe(tsNanos = 1000L, dir = Dir.UP, speed = 2.0f)
        val touchUp = LaneSwipeMapper.map(swipeUp, profileWithoutTargets)
        assertThat(touchUp.to.x).isWithin(1e-5f).of(0.5f)
        assertThat(touchUp.to.y).isWithin(1e-5f).of(0.2f)

        val swipeDown = Swipe(tsNanos = 1000L, dir = Dir.DOWN, speed = 2.0f)
        val touchDown = LaneSwipeMapper.map(swipeDown, profileWithoutTargets)
        assertThat(touchDown.to.x).isWithin(1e-5f).of(0.5f)
        assertThat(touchDown.to.y).isWithin(1e-5f).of(0.8f)
    }
}
