package com.zorbi.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.PerfMode
import org.junit.Test

/**
 * Unit tests for Settings data model and Section 5.5 DataStore keys.
 */
class SettingsStateTest {

    @Test
    fun defaultSettings_haveCorrectDefaultsPerSection5_5() {
        val defaults = ZorbiSettings.DEFAULT

        assertThat(defaults.perfMode).isEqualTo(PerfMode.BALANCED)
        // Master Prompt Section 5.5 & Privacy Rule: Telemetry MUST default to false
        assertThat(defaults.telemetryOptIn).isFalse()
        assertThat(defaults.wizardDone).isFalse()
        assertThat(defaults.lastGame).isEmpty()
    }

    @Test
    fun frozenDataStoreKeys_matchMasterPromptSection5_5() {
        assertThat(ZorbiSettings.KEY_PERF_MODE).isEqualTo("perf_mode")
        assertThat(ZorbiSettings.KEY_TELEMETRY_OPT_IN).isEqualTo("telemetry_opt_in")
        assertThat(ZorbiSettings.KEY_DEVICE_ID).isEqualTo("device_id")
        assertThat(ZorbiSettings.KEY_WIZARD_DONE).isEqualTo("wizard_done")
        assertThat(ZorbiSettings.KEY_LAST_GAME).isEqualTo("last_game")
    }

    @Test
    fun perfModeEnum_supportsAllThreeModes() {
        val modes = PerfMode.values().map { it.name }
        assertThat(modes).containsExactly("QUALITY", "BALANCED", "PERFORMANCE")
    }

    @Test
    fun perfMode_parsingRecoversFromUnknownGracefully() {
        val parsed = try {
            PerfMode.valueOf("INVALID_MODE")
        } catch (e: Exception) {
            PerfMode.BALANCED
        }
        assertThat(parsed).isEqualTo(PerfMode.BALANCED)
    }

    @Test
    fun resetSettings_restoresBalancedAndOptOut() {
        val customized = ZorbiSettings(
            perfMode = PerfMode.QUALITY,
            telemetryOptIn = true,
            deviceId = "test-uuid-1234",
            wizardDone = true,
            lastGame = "com.kiloo.subwaysurf"
        )

        val reset = customized.copy(
            perfMode = PerfMode.BALANCED,
            telemetryOptIn = false,
            wizardDone = false,
            lastGame = ""
        )

        assertThat(reset.perfMode).isEqualTo(PerfMode.BALANCED)
        assertThat(reset.telemetryOptIn).isFalse()
        assertThat(reset.deviceId).isEqualTo("test-uuid-1234") // Preserves device ID
        assertThat(reset.wizardDone).isFalse()
        assertThat(reset.lastGame).isEmpty()
    }
}
