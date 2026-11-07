package com.zorbi.ui.screens.wizard

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit verification for Setup Wizard State & Evaluation Logic.
 * Enforces Master Prompt Section 8 / Task P3-2 & Test Gate T-P3-2.
 */
class WizardStateTest {

    @Test
    fun verifyInitialFreshInstallState() {
        val freshState = WizardState(
            isAccessibilityGranted = false,
            isCameraGranted = false,
            isNotificationGranted = false,
            isBatteryOptimized = false
        )
        assertThat(freshState.totalSteps).isEqualTo(4)
        assertThat(freshState.grantedCount).isEqualTo(0)
        assertThat(freshState.isAllGreen).isFalse()
    }

    @Test
    fun verifyPartialStateCalculations() {
        val state1 = WizardState(
            isAccessibilityGranted = true,
            isCameraGranted = false,
            isNotificationGranted = false,
            isBatteryOptimized = false
        )
        assertThat(state1.grantedCount).isEqualTo(1)
        assertThat(state1.isAllGreen).isFalse()

        val state2 = state1.copy(isCameraGranted = true)
        assertThat(state2.grantedCount).isEqualTo(2)
        assertThat(state2.isAllGreen).isFalse()

        val state3 = state2.copy(isNotificationGranted = true)
        assertThat(state3.grantedCount).isEqualTo(3)
        assertThat(state3.isAllGreen).isFalse()
    }

    @Test
    fun verifyAllGreenRequirement() {
        val allGreenState = WizardState(
            isAccessibilityGranted = true,
            isCameraGranted = true,
            isNotificationGranted = true,
            isBatteryOptimized = true
        )
        assertThat(allGreenState.grantedCount).isEqualTo(4)
        assertThat(allGreenState.isAllGreen).isTrue()

        // Strict: Any single failure prevents "All Green"
        assertThat(allGreenState.copy(isAccessibilityGranted = false).isAllGreen).isFalse()
        assertThat(allGreenState.copy(isCameraGranted = false).isAllGreen).isFalse()
        assertThat(allGreenState.copy(isNotificationGranted = false).isAllGreen).isFalse()
        assertThat(allGreenState.copy(isBatteryOptimized = false).isAllGreen).isFalse()
    }
}
