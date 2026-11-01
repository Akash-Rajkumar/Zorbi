package com.zorbi.fakes.p2

import com.zorbi.core.*

class FakeGestureInterpreter(
    override val accepts: TrackerId = TrackerId.HAND_SINGLE,
    var queuedEvents: List<GestureEvent> = emptyList()
) : GestureInterpreter {
    override fun onFrame(frame: TrackerFrame, profile: GameProfile, cal: Calibration): List<GestureEvent> {
        return queuedEvents
    }
}

