package com.zorbi.ui.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests verifying GameLauncher contract and result models.
 */
class GameLauncherTest {

    @Test
    fun verifyGameLaunchResultHierarchy() {
        val success: GameLaunchResult = GameLaunchResult.Success
        assertThat(success).isEqualTo(GameLaunchResult.Success)

        val notInstalled: GameLaunchResult = GameLaunchResult.NotInstalled("com.test.game")
        assertThat(notInstalled).isInstanceOf(GameLaunchResult.NotInstalled::class.java)
        assertThat((notInstalled as GameLaunchResult.NotInstalled).packageName).isEqualTo("com.test.game")

        val error: GameLaunchResult = GameLaunchResult.Error("ActivityNotFound")
        assertThat(error).isInstanceOf(GameLaunchResult.Error::class.java)
        assertThat((error as GameLaunchResult.Error).message).isEqualTo("ActivityNotFound")
    }
}
