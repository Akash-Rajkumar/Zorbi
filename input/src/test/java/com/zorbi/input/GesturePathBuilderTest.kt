package com.zorbi.input

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Pt
import org.junit.Assert.assertThrows
import org.junit.Test

class GesturePathBuilderTest {

    @Test
    fun testNormalizeToPixel_centerPoint() {
        val pt = Pt(0.5f, 0.5f)
        val pixel = GesturePathBuilder.toPixel(pt, 1080, 1920)

        assertThat(pixel.x).isEqualTo(540f)
        assertThat(pixel.y).isEqualTo(960f)
    }

    @Test
    fun testNormalizeToPixel_corners() {
        val origin = Pt(0.0f, 0.0f)
        val originPixel = GesturePathBuilder.toPixel(origin, 1080, 2400)
        assertThat(originPixel.x).isEqualTo(0f)
        assertThat(originPixel.y).isEqualTo(0f)

        val max = Pt(1.0f, 1.0f)
        val maxPixel = GesturePathBuilder.toPixel(max, 1080, 2400)
        assertThat(maxPixel.x).isEqualTo(1080f)
        assertThat(maxPixel.y).isEqualTo(2400f)
    }

    @Test
    fun testNormalizeToPixel_clampingOutOfBounds() {
        val outOfBoundsNegative = Pt(-0.25f, -0.5f)
        val clampedNeg = GesturePathBuilder.toPixel(outOfBoundsNegative, 1080, 1920)
        assertThat(clampedNeg.x).isEqualTo(0f)
        assertThat(clampedNeg.y).isEqualTo(0f)

        val outOfBoundsPositive = Pt(1.25f, 1.5f)
        val clampedPos = GesturePathBuilder.toPixel(outOfBoundsPositive, 1080, 1920)
        assertThat(clampedPos.x).isEqualTo(1080f)
        assertThat(clampedPos.y).isEqualTo(1920f)
    }

    @Test
    fun testNormalizeToPixel_invalidDisplayBounds_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            GesturePathBuilder.toPixel(Pt(0.5f, 0.5f), 0, 1920)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GesturePathBuilder.toPixel(Pt(0.5f, 0.5f), 1080, -100)
        }
    }

    @Test
    fun testClampDuration_validValues() {
        assertThat(GesturePathBuilder.clampDuration(150L)).isEqualTo(150L)
        assertThat(GesturePathBuilder.clampDuration(300L)).isEqualTo(300L)
    }

    @Test
    fun testClampDuration_zeroAndNegative_usesDefault() {
        assertThat(GesturePathBuilder.clampDuration(0L, 50L)).isEqualTo(50L)
        assertThat(GesturePathBuilder.clampDuration(-10L, 100L)).isEqualTo(100L)
    }

    @Test
    fun testClampDuration_belowMinimum_clampedToMin() {
        assertThat(GesturePathBuilder.clampDuration(5L))
            .isEqualTo(GesturePathBuilder.MIN_GESTURE_DURATION_MS)
    }

    @Test
    fun testClampDuration_aboveMaximum_clampedToMax() {
        assertThat(GesturePathBuilder.clampDuration(10000L))
            .isEqualTo(GesturePathBuilder.MAX_GESTURE_DURATION_MS)
    }

    @Test
    fun testMultiPointPath_emptyList_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            GesturePathBuilder.createMultiPointPath(emptyList())
        }
    }
}

