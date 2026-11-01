package com.zorbi.vision

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.PerfMode
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerStats
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for [RealTrackerRegistry] (Gate I-2 / Phase 1).
 */
class RealTrackerRegistryTest {

    private class TestTracker(override val id: TrackerId) : Tracker {
        val _frames = MutableSharedFlow<TrackerFrame>()
        override val frames: SharedFlow<TrackerFrame> = _frames

        val _stats = MutableStateFlow(TrackerStats(0f, 0f, 0f, 0))
        override val stats: StateFlow<TrackerStats> = _stats

        var isStarted = false
        var isStopped = false

        override suspend fun start(perf: PerfMode) {
            isStarted = true
            isStopped = false
        }

        override suspend fun stop() {
            isStopped = true
            isStarted = false
        }
    }

    @Test
    fun get_returnsConfiguredTrackerFromFactory() {
        val singleTracker = TestTracker(TrackerId.HAND_SINGLE)
        val wheelTracker = TestTracker(TrackerId.HAND_DUAL_WHEEL)
        val poseTracker = TestTracker(TrackerId.POSE_LITE)

        var singleCreatedCount = 0
        var wheelCreatedCount = 0
        var poseCreatedCount = 0

        val registry = RealTrackerRegistry(
            context = android.app.Application(), // mocked or unused when custom factory provides instance
            customTrackerFactories = mapOf(
                TrackerId.HAND_SINGLE to {
                    singleCreatedCount++
                    singleTracker
                },
                TrackerId.HAND_DUAL_WHEEL to {
                    wheelCreatedCount++
                    wheelTracker
                },
                TrackerId.POSE_LITE to {
                    poseCreatedCount++
                    poseTracker
                }
            )
        )

        // Verify lazy instantiation: none created before get()
        assertThat(registry.isInstantiated(TrackerId.HAND_SINGLE)).isFalse()
        assertThat(registry.isInstantiated(TrackerId.HAND_DUAL_WHEEL)).isFalse()
        assertThat(registry.isInstantiated(TrackerId.POSE_LITE)).isFalse()

        // Fetch HAND_SINGLE
        val retrievedSingle = registry.get(TrackerId.HAND_SINGLE)
        assertThat(retrievedSingle).isSameInstanceAs(singleTracker)
        assertThat(singleCreatedCount).isEqualTo(1)
        assertThat(registry.isInstantiated(TrackerId.HAND_SINGLE)).isTrue()

        // Fetch HAND_DUAL_WHEEL
        val retrievedWheel = registry.get(TrackerId.HAND_DUAL_WHEEL)
        assertThat(retrievedWheel).isSameInstanceAs(wheelTracker)
        assertThat(wheelCreatedCount).isEqualTo(1)
        assertThat(registry.isInstantiated(TrackerId.HAND_DUAL_WHEEL)).isTrue()

        // Fetch POSE_LITE
        val retrievedPose = registry.get(TrackerId.POSE_LITE)
        assertThat(retrievedPose).isSameInstanceAs(poseTracker)
        assertThat(poseCreatedCount).isEqualTo(1)
        assertThat(registry.isInstantiated(TrackerId.POSE_LITE)).isTrue()
    }

    @Test
    fun get_cachesAndReusesTrackerInstance() {
        val singleTracker = TestTracker(TrackerId.HAND_SINGLE)
        var creationCount = 0

        val registry = RealTrackerRegistry(
            context = android.app.Application(),
            customTrackerFactories = mapOf(
                TrackerId.HAND_SINGLE to {
                    creationCount++
                    singleTracker
                }
            )
        )

        val first = registry.get(TrackerId.HAND_SINGLE)
        val second = registry.get(TrackerId.HAND_SINGLE)
        val third = registry.get(TrackerId.HAND_SINGLE)

        assertThat(first).isSameInstanceAs(singleTracker)
        assertThat(second).isSameInstanceAs(singleTracker)
        assertThat(third).isSameInstanceAs(singleTracker)
        assertThat(creationCount).isEqualTo(1)
    }

    @Test
    fun releaseAll_stopsAndClearsAllTrackers() = runTest {
        val singleTracker = TestTracker(TrackerId.HAND_SINGLE)
        val wheelTracker = TestTracker(TrackerId.HAND_DUAL_WHEEL)

        val registry = RealTrackerRegistry(
            context = android.app.Application(),
            customTrackerFactories = mapOf(
                TrackerId.HAND_SINGLE to { singleTracker },
                TrackerId.HAND_DUAL_WHEEL to { wheelTracker }
            )
        )

        singleTracker.start(PerfMode.BALANCED)
        wheelTracker.start(PerfMode.BALANCED)

        registry.get(TrackerId.HAND_SINGLE)
        registry.get(TrackerId.HAND_DUAL_WHEEL)

        assertThat(registry.isInstantiated(TrackerId.HAND_SINGLE)).isTrue()
        assertThat(registry.isInstantiated(TrackerId.HAND_DUAL_WHEEL)).isTrue()

        registry.releaseAll()

        assertThat(singleTracker.isStopped).isTrue()
        assertThat(wheelTracker.isStopped).isTrue()
        assertThat(registry.isInstantiated(TrackerId.HAND_SINGLE)).isFalse()
        assertThat(registry.isInstantiated(TrackerId.HAND_DUAL_WHEEL)).isFalse()
    }
}

