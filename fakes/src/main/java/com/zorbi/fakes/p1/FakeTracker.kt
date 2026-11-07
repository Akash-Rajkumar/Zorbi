package com.zorbi.fakes.p1

import com.zorbi.core.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class FakeTracker(
    override val id: TrackerId = TrackerId.HAND_SINGLE
) : Tracker {
    private val _frames = MutableSharedFlow<TrackerFrame>()
    override val frames: SharedFlow<TrackerFrame> = _frames

    private val _stats = MutableStateFlow(TrackerStats(fps = 30f, inferenceMsP50 = 12f, inferenceMsP95 = 16f, dropped = 0))
    override val stats: StateFlow<TrackerStats> = _stats

    var isStarted: Boolean = false
        private set

    override suspend fun start(perf: PerfMode) {
        isStarted = true
    }

    override suspend fun stop() {
        isStarted = false
    }

    suspend fun emitFrame(frame: TrackerFrame) {
        _frames.emit(frame)
    }

    fun updateStats(stats: TrackerStats) {
        _stats.value = stats
    }
}

class FakeTrackerRegistry(
    private val trackers: Map<TrackerId, Tracker> = mapOf(
        TrackerId.HAND_SINGLE to FakeTracker(TrackerId.HAND_SINGLE),
        TrackerId.HAND_DUAL_WHEEL to FakeTracker(TrackerId.HAND_DUAL_WHEEL),
        TrackerId.POSE_LITE to FakeTracker(TrackerId.POSE_LITE)
    )
) : TrackerRegistry {
    override fun get(id: TrackerId): Tracker =
        trackers[id] ?: error("No tracker registered for $id")
}

