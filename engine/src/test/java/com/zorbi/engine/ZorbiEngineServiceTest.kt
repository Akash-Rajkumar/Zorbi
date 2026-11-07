package com.zorbi.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests verifying ZorbiEngineService constants, actions, and lifecycle contract.
 */
class ZorbiEngineServiceTest {

    @Test
    fun verifyServiceConstantsAndActionContracts() {
        assertThat(ZorbiEngineService.CHANNEL_ID).isEqualTo("zorbi_engine")
        assertThat(ZorbiEngineService.NOTIFICATION_ID).isEqualTo(2026)
        assertThat(ZorbiEngineService.ACTION_START).isEqualTo("com.zorbi.engine.START")
        assertThat(ZorbiEngineService.ACTION_STOP).isEqualTo("com.zorbi.engine.STOP")
        assertThat(ZorbiEngineService.EXTRA_PACKAGE_NAME).isEqualTo("extra_package_name")
    }

    @Test
    fun verifyInitialRunningState() {
        assertThat(ZorbiEngineService.isRunning.value).isFalse()
    }
}
