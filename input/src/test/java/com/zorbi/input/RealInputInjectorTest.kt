package com.zorbi.input

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Pt
import org.junit.Test

/**
 * Unit tests for RealInputInjector verifying coordinate normalization, dynamic orientation bounds,
 * and safety when service is unavailable.
 */
class RealInputInjectorTest {

    @Test
    fun verifyPortraitCoordinateMapping() {
        val injector = RealInputInjector(
            serviceProvider = { null },
            dimensionProvider = { Pair(1080f, 2400f) }
        )

        // Center
        val (centerX, centerY) = injector.mapToPixels(Pt(0.5f, 0.5f))
        assertThat(centerX).isEqualTo(540f)
        assertThat(centerY).isEqualTo(1200f)

        // Top-left
        val (tlX, tlY) = injector.mapToPixels(Pt(0f, 0f))
        assertThat(tlX).isEqualTo(0f)
        assertThat(tlY).isEqualTo(0f)

        // Bottom-right
        val (brX, brY) = injector.mapToPixels(Pt(1f, 1f))
        assertThat(brX).isEqualTo(1080f)
        assertThat(brY).isEqualTo(2400f)
    }

    @Test
    fun verifyLandscapeCoordinateMapping() {
        // Landscape phone dimensions (e.g. Hill Climb Racing on Vivo V2168)
        val injector = RealInputInjector(
            serviceProvider = { null },
            dimensionProvider = { Pair(2400f, 1080f) }
        )

        // Gas pedal (typically x=0.89, y=0.82)
        val (gasX, gasY) = injector.mapToPixels(Pt(0.89f, 0.82f))
        assertThat(gasX).isWithin(1.0f).of(2136f)
        assertThat(gasY).isWithin(1.0f).of(885.6f)

        // Brake pedal (typically x=0.14, y=0.82)
        val (brakeX, brakeY) = injector.mapToPixels(Pt(0.14f, 0.82f))
        assertThat(brakeX).isWithin(1.0f).of(336f)
        assertThat(brakeY).isWithin(1.0f).of(885.6f)
    }

    @Test
    fun verifyClampingOutOfBoundCoordinates() {
        val injector = RealInputInjector(
            serviceProvider = { null },
            dimensionProvider = { Pair(1080f, 2400f) }
        )

        val (negativeX, negativeY) = injector.mapToPixels(Pt(-0.5f, -1.0f))
        assertThat(negativeX).isEqualTo(0f)
        assertThat(negativeY).isEqualTo(0f)

        val (overX, overY) = injector.mapToPixels(Pt(1.5f, 2.0f))
        assertThat(overX).isEqualTo(1080f)
        assertThat(overY).isEqualTo(2400f)
    }

    @Test
    fun verifyNullServiceSafety() {
        // Ensure that calling injector methods without an active accessibility service does not throw
        val injector = RealInputInjector(
            serviceProvider = { null },
            dimensionProvider = { Pair(1080f, 2400f) }
        )

        injector.tap(Pt(0.5f, 0.5f))
        injector.swipe(Pt(0.2f, 0.5f), Pt(0.8f, 0.5f), 150L)
        injector.path(listOf(Pt(0.1f, 0.1f), Pt(0.5f, 0.5f), Pt(0.9f, 0.9f)), 200L)
        injector.holdAt(Pt(0.5f, 0.5f))
        injector.dragTo(Pt(0.6f, 0.6f))
        injector.release()

        // Test passes if no exception is thrown
    }
}
