package com.zorbi.fakes

import com.zorbi.core.*
import com.zorbi.fakes.p1.FakeCalibrationRepository
import com.zorbi.fakes.p1.FakeTrackerRegistry
import com.zorbi.fakes.p2.FakeEngineController
import com.zorbi.fakes.p2.FakeInputInjector
import com.zorbi.fakes.p2.FakeProfileRepository
import com.zorbi.fakes.p4.FakeTelemetrySink

class FakeAppGraph(
    override val trackers: TrackerRegistry = FakeTrackerRegistry(),
    override val engine: EngineController = FakeEngineController(),
    override val profiles: ProfileRepository = FakeProfileRepository(),
    override val calibration: CalibrationRepository = FakeCalibrationRepository(),
    override val telemetry: TelemetrySink = FakeTelemetrySink(),
    override val injector: InputInjector = FakeInputInjector()
) : AppGraph

