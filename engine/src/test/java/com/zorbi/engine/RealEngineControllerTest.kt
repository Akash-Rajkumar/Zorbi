package com.zorbi.engine

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.zorbi.core.EngineState
import com.zorbi.core.PerfMode
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests verifying RealEngineController state transitions and lifecycle.
 */
class RealEngineControllerTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val controller = RealEngineController(mockContext)

    @Test
    fun verifyInitialStateIsIdle() {
        assertThat(controller.state.value).isEqualTo(EngineState.IDLE)
        assertThat(controller.activePackage).isNull()
    }

    @Test
    fun verifyStartTransitionsToRunning() = runTest {
        controller.start("com.fingersoft.hillclimb", PerfMode.BALANCED)
        assertThat(controller.state.value).isEqualTo(EngineState.RUNNING)
        assertThat(controller.activePackage).isEqualTo("com.fingersoft.hillclimb")
        assertThat(controller.activePerf).isEqualTo(PerfMode.BALANCED)
    }

    @Test
    fun verifyPauseAndResumeTransitions() = runTest {
        controller.start("com.fingersoft.hillclimb", PerfMode.BALANCED)
        controller.pause()
        assertThat(controller.state.value).isEqualTo(EngineState.PAUSED)

        controller.resume()
        assertThat(controller.state.value).isEqualTo(EngineState.RUNNING)
    }

    @Test
    fun verifyStopTransitionsToIdleAndClearsState() = runTest {
        controller.start("com.fingersoft.hillclimb", PerfMode.BALANCED)
        controller.stop()
        assertThat(controller.state.value).isEqualTo(EngineState.IDLE)
        assertThat(controller.activePackage).isNull()
        assertThat(controller.activePerf).isNull()
    }
}
