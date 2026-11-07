package com.zorbi.ui.haptics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit test verifying haptic contracts and headless safety.
 */
class ZorbiHapticsTest {

    @Test
    fun noOpHaptics_safelyExecutesAllEventsWithoutCrashing() {
        val haptics: ZorbiHaptics = NoOpZorbiHaptics

        // Should complete smoothly without throws
        haptics.click()
        haptics.confirm()
        haptics.warning()
        haptics.tick()
        haptics.toggle()

        assertThat(haptics).isNotNull()
    }

    @Test
    fun androidZorbiHaptics_handlesNullContextAndNullViewGracefully() {
        val haptics = AndroidZorbiHaptics(view = null, context = null)

        // Headless environment safety check
        haptics.click()
        haptics.confirm()
        haptics.warning()
        haptics.tick()
        haptics.toggle()

        assertThat(haptics).isNotNull()
    }
}
