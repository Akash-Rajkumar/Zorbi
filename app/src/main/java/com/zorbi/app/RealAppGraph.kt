package com.zorbi.app

import com.zorbi.core.*

class RealAppGraph(
    override val trackers: TrackerRegistry,
    override val engine: EngineController,
    override val profiles: ProfileRepository,
    override val calibration: CalibrationRepository,
    override val telemetry: TelemetrySink,
    override val injector: InputInjector
) : AppGraph

