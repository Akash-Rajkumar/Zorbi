package com.zorbi.vision.perf

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.PerfMode
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PerfControllerTest {

    private fun makeStats(p95: Float, fps: Float = 30f, p50: Float = p95 * 0.8f, dropped: Int = 0): TrackerStats {
        return TrackerStats(fps = fps, inferenceMsP50 = p50, inferenceMsP95 = p95, dropped = dropped)
    }

    // -------------------------------------------------------------------------
    // 1. Normal Performance Stays in Current Mode
    // -------------------------------------------------------------------------

    @Test
    fun normalPerformance_maintainsInitialQualityMode() {
        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f
        )

        val stats = makeStats(p95 = 55.0f)
        for (i in 0 until 20) {
            val mode = controller.onStatsUpdated(stats)
            assertThat(mode).isEqualTo(PerfMode.QUALITY)
        }

        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)
        assertThat(controller.isOverloaded).isFalse()
        assertThat(controller.consecutiveOverBudget).isEqualTo(0)
    }

    @Test
    fun normalPerformance_maintainsBalancedMode() {
        val controller = PerfController(
            initialMode = PerfMode.BALANCED,
            p95BudgetMs = 70.0f
        )

        val stats = makeStats(p95 = 45.0f)
        val mode = controller.onStatsUpdated(stats)

        assertThat(mode).isEqualTo(PerfMode.BALANCED)
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.BALANCED)
        assertThat(controller.isOverloaded).isFalse()
    }

    // -------------------------------------------------------------------------
    // 2. Excessive P95 Triggers PERFORMANCE
    // -------------------------------------------------------------------------

    @Test
    fun excessiveP95_triggersPerformanceAfterConsecutiveViolations() {
        var callbackInvoked = false
        var capturedNewMode: PerfMode? = null
        var capturedPrevMode: PerfMode? = null

        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f,
            dropConsecutiveSamples = 2,
            onModeChanged = { newMode, prevMode ->
                callbackInvoked = true
                capturedNewMode = newMode
                capturedPrevMode = prevMode
            }
        )

        val overloadStats = makeStats(p95 = 95.0f)

        // 1st sample: violation count becomes 1, but threshold is 2 -> remains QUALITY
        val mode1 = controller.onStatsUpdated(overloadStats)
        assertThat(mode1).isEqualTo(PerfMode.QUALITY)
        assertThat(controller.consecutiveOverBudget).isEqualTo(1)
        assertThat(callbackInvoked).isFalse()

        // 2nd sample: threshold met -> drops to PERFORMANCE
        val mode2 = controller.onStatsUpdated(overloadStats)
        assertThat(mode2).isEqualTo(PerfMode.PERFORMANCE)
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.PERFORMANCE)
        assertThat(controller.isOverloaded).isTrue()
        assertThat(callbackInvoked).isTrue()
        assertThat(capturedNewMode).isEqualTo(PerfMode.PERFORMANCE)
        assertThat(capturedPrevMode).isEqualTo(PerfMode.QUALITY)
    }

    @Test
    fun singleExcessiveSpike_doesNotImmediatelyDropWhenThresholdIsTwo() {
        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f,
            dropConsecutiveSamples = 2
        )

        // Single isolated spike (e.g. GC pause)
        controller.onStatsUpdated(makeStats(p95 = 120.0f))
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)
        assertThat(controller.isOverloaded).isFalse()

        // Followed by normal latency -> violation count is cleared
        controller.onStatsUpdated(makeStats(p95 = 40.0f))
        assertThat(controller.consecutiveOverBudget).isEqualTo(0)
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)
    }

    // -------------------------------------------------------------------------
    // 3. Boundary Behavior Around the Budget
    // -------------------------------------------------------------------------

    @Test
    fun boundary_exactlyAtBudget_doesNotTriggerDrop() {
        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f,
            dropConsecutiveSamples = 2
        )

        // Exactly 80.0f is within budget, not strictly greater
        for (i in 0 until 5) {
            controller.onStatsUpdated(makeStats(p95 = 80.0f))
        }

        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)
        assertThat(controller.isOverloaded).isFalse()
    }

    @Test
    fun boundary_slightExceedance_triggersDropAfterThreshold() {
        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f,
            dropConsecutiveSamples = 2
        )

        controller.onStatsUpdated(makeStats(p95 = 80.1f))
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)

        controller.onStatsUpdated(makeStats(p95 = 80.1f))
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.PERFORMANCE)
        assertThat(controller.isOverloaded).isTrue()
    }

    @Test
    fun deadband_betweenRecoveryAndBudget_preservesCurrentMode() {
        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f,
            recoveryRatio = 0.75f, // deadband is [60.0ms, 80.0ms]
            dropConsecutiveSamples = 2,
            recoveryConsecutiveSamples = 2
        )

        // Case A: currently in QUALITY, latency 70ms (deadband) -> remains QUALITY
        controller.onStatsUpdated(makeStats(p95 = 70.0f))
        controller.onStatsUpdated(makeStats(p95 = 70.0f))
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)

        // Now trigger drop to PERFORMANCE
        controller.onStatsUpdated(makeStats(p95 = 90.0f))
        controller.onStatsUpdated(makeStats(p95 = 90.0f))
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.PERFORMANCE)

        // Case B: currently in PERFORMANCE, latency 70ms (deadband) -> remains PERFORMANCE
        controller.onStatsUpdated(makeStats(p95 = 70.0f))
        controller.onStatsUpdated(makeStats(p95 = 70.0f))
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.PERFORMANCE)
    }

    // -------------------------------------------------------------------------
    // 4. Repeated Stats & Anti-Oscillation (Hysteresis)
    // -------------------------------------------------------------------------

    @Test
    fun oscillatingStats_aroundBudgetBoundary_preventsModeChurn() {
        var modeChangeCount = 0
        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f,
            recoveryRatio = 0.75f, // 60ms
            dropConsecutiveSamples = 2,
            recoveryConsecutiveSamples = 2,
            onModeChanged = { _, _ -> modeChangeCount++ }
        )

        // Latencies oscillating near budget: 82ms, 78ms, 82ms, 78ms...
        val oscillating = listOf(82f, 78f, 82f, 78f, 82f, 78f, 82f, 78f)
        for (p95 in oscillating) {
            controller.onStatsUpdated(makeStats(p95))
        }

        // Because 78ms is in the deadband, it clears the consecutive over-budget counter.
        // Therefore, mode never churns back and forth!
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)
        assertThat(modeChangeCount).isEqualTo(0)
    }

    @Test
    fun sustainedRecovery_switchesBackToTargetMode() {
        var recovered = false
        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f,
            recoveryRatio = 0.75f, // recovery threshold = 60ms
            dropConsecutiveSamples = 1,
            recoveryConsecutiveSamples = 3,
            onModeChanged = { newMode, _ ->
                if (newMode == PerfMode.QUALITY) recovered = true
            }
        )

        // 1. Force drop to PERFORMANCE
        controller.onStatsUpdated(makeStats(p95 = 100.0f))
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.PERFORMANCE)
        assertThat(controller.isOverloaded).isTrue()

        // 2. Feed 2 recovery samples (< 60ms), threshold is 3
        controller.onStatsUpdated(makeStats(p95 = 45.0f))
        controller.onStatsUpdated(makeStats(p95 = 48.0f))
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.PERFORMANCE)
        assertThat(recovered).isFalse()

        // 3. 3rd recovery sample -> recovers back to QUALITY
        controller.onStatsUpdated(makeStats(p95 = 46.0f))
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)
        assertThat(controller.isOverloaded).isFalse()
        assertThat(recovered).isTrue()
    }

    // -------------------------------------------------------------------------
    // 5. Reset and Lifecycle Behavior
    // -------------------------------------------------------------------------

    @Test
    fun reset_restoresInitialModeAndClearsCounters() {
        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f,
            dropConsecutiveSamples = 1
        )

        controller.onStatsUpdated(makeStats(p95 = 100.0f))
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.PERFORMANCE)
        assertThat(controller.isOverloaded).isTrue()

        controller.reset()
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)
        assertThat(controller.isOverloaded).isFalse()
        assertThat(controller.consecutiveOverBudget).isEqualTo(0)
        assertThat(controller.consecutiveUnderBudget).isEqualTo(0)
    }

    @Test
    fun zeroOrNegativeStats_ignoredBeforeInferenceBegins() {
        val controller = PerfController(initialMode = PerfMode.QUALITY)

        controller.onStatsUpdated(makeStats(p95 = 0f))
        controller.onStatsUpdated(makeStats(p95 = -10f))

        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)
        assertThat(controller.consecutiveOverBudget).isEqualTo(0)
    }

    @Test
    fun setTargetMode_updatesModeImmediatelyWhenNotOverloaded() {
        val controller = PerfController(initialMode = PerfMode.QUALITY)

        controller.setTargetMode(PerfMode.BALANCED)
        assertThat(controller.targetMode).isEqualTo(PerfMode.BALANCED)
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.BALANCED)
    }

    @Test
    fun setTargetMode_whileOverloaded_remainsInPerformanceUntilRecovery() {
        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            dropConsecutiveSamples = 1,
            recoveryConsecutiveSamples = 1
        )

        // Drop to PERFORMANCE
        controller.onStatsUpdated(makeStats(p95 = 100f))
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.PERFORMANCE)

        // Update target mode to BALANCED
        controller.setTargetMode(PerfMode.BALANCED)
        assertThat(controller.targetMode).isEqualTo(PerfMode.BALANCED)
        // Remains in PERFORMANCE while overloaded
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.PERFORMANCE)

        // Once load subsides, recovers to the new target mode (BALANCED)
        controller.onStatsUpdated(makeStats(p95 = 40f))
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.BALANCED)
    }

    @Test
    fun constructorValidation_rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException::class.java) {
            PerfController(p95BudgetMs = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PerfController(recoveryRatio = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PerfController(recoveryRatio = 1.0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PerfController(dropConsecutiveSamples = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PerfController(recoveryConsecutiveSamples = 0)
        }
    }

    // -------------------------------------------------------------------------
    // 6. Coroutine Observation & Tracker Integration
    // -------------------------------------------------------------------------

    @Test
    fun observe_reactsToIncomingStatsFlow() = runTest {
        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f,
            dropConsecutiveSamples = 1
        )

        val statsFlow = MutableStateFlow(makeStats(p95 = 50f))
        val job = controller.observe(statsFlow, this)

        advanceUntilIdle()
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)

        // Emit overloaded stats
        statsFlow.value = makeStats(p95 = 95f)
        advanceUntilIdle()
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.PERFORMANCE)

        job.cancel()
    }

    @Test
    fun attachToTracker_invokesTrackerStartOnOverloadAndRecovery() = runTest {
        val startedModes = mutableListOf<PerfMode>()
        val trackerStatsFlow = MutableStateFlow(makeStats(p95 = 50f))

        val fakeTracker = object : Tracker {
            override val id: TrackerId = TrackerId.HAND_SINGLE
            override val frames: SharedFlow<TrackerFrame> = MutableSharedFlow()
            override val stats: StateFlow<TrackerStats> = trackerStatsFlow

            override suspend fun start(perf: PerfMode) {
                startedModes.add(perf)
            }

            override suspend fun stop() {}
        }

        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f,
            dropConsecutiveSamples = 1,
            recoveryConsecutiveSamples = 1
        )

        val job = controller.attachToTracker(fakeTracker, this)
        advanceUntilIdle()
        assertThat(startedModes).isEmpty()

        // 1. Emit overload -> triggers tracker.start(PERFORMANCE)
        trackerStatsFlow.value = makeStats(p95 = 110f)
        advanceUntilIdle()
        assertThat(startedModes).containsExactly(PerfMode.PERFORMANCE)

        // 2. Emit recovery -> triggers tracker.start(QUALITY)
        trackerStatsFlow.value = makeStats(p95 = 40f)
        advanceUntilIdle()
        assertThat(startedModes).containsExactly(PerfMode.PERFORMANCE, PerfMode.QUALITY).inOrder()

        controller.detach()
    }

    @Test
    fun attachToTracker_cancelsPreviousJob_preventsDuplicateCollectors() = runTest {
        val startCalls = mutableListOf<PerfMode>()
        val trackerStatsFlow = MutableStateFlow(makeStats(p95 = 50f))

        val fakeTracker = object : Tracker {
            override val id: TrackerId = TrackerId.HAND_SINGLE
            override val frames: SharedFlow<TrackerFrame> = MutableSharedFlow()
            override val stats: StateFlow<TrackerStats> = trackerStatsFlow
            override suspend fun start(perf: PerfMode) {
                startCalls.add(perf)
            }
            override suspend fun stop() {}
        }

        val controller = PerfController(dropConsecutiveSamples = 1)

        val firstJob = controller.attachToTracker(fakeTracker, this)
        assertThat(firstJob.isActive).isTrue()

        // Calling attachToTracker again cancels the first job
        val secondJob = controller.attachToTracker(fakeTracker, this)
        advanceUntilIdle()
        assertThat(firstJob.isCancelled).isTrue()
        assertThat(secondJob.isActive).isTrue()

        // Overload must be received exactly once
        trackerStatsFlow.value = makeStats(p95 = 110f)
        advanceUntilIdle()
        assertThat(startCalls).containsExactly(PerfMode.PERFORMANCE)

        controller.detach()
        assertThat(secondJob.isCancelled).isTrue()
    }

    @Test
    fun attachToTracker_whenStartThrows_survivesWithoutCrashing() = runTest {
        var startAttempted = false
        val trackerStatsFlow = MutableStateFlow(makeStats(p95 = 50f))

        val failingTracker = object : Tracker {
            override val id: TrackerId = TrackerId.HAND_SINGLE
            override val frames: SharedFlow<TrackerFrame> = MutableSharedFlow()
            override val stats: StateFlow<TrackerStats> = trackerStatsFlow
            override suspend fun start(perf: PerfMode) {
                startAttempted = true
                throw RuntimeException("Simulated camera rebind failure")
            }
            override suspend fun stop() {}
        }

        val controller = PerfController(dropConsecutiveSamples = 1)
        val job = controller.attachToTracker(failingTracker, this)
        advanceUntilIdle()

        // Emit overload - tracker.start will throw, but controller must survive
        trackerStatsFlow.value = makeStats(p95 = 110f)
        advanceUntilIdle()

        assertThat(startAttempted).isTrue()
        assertThat(job.isActive).isTrue() // Coroutine did not crash

        controller.detach()
    }
}
