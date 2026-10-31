package com.zorbi.fakes.p4

import com.zorbi.core.GameProfile
import com.zorbi.core.PerfMode
import com.zorbi.core.TelemetrySink
import com.zorbi.core.TrackerStats

class FakeTelemetrySink : TelemetrySink {
    val recordedSessions = mutableListOf<String>()
    val recordedEvents = mutableListOf<String>()

    override fun session(
        started: Long,
        p: GameProfile,
        stats: TrackerStats,
        durationS: Int,
        gestures: Int,
        perf: PerfMode
    ) {
        recordedSessions.add("${p.packageName} dur=${durationS}s gestures=$gestures")
    }

    override fun gestureEvent(sessionId: String, type: String, magnitude: Float) {
        recordedEvents.add("$sessionId: $type mag=$magnitude")
    }
}

