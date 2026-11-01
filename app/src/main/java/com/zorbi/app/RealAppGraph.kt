package com.zorbi.app

import android.content.Context
import android.util.Log
import com.zorbi.core.AppGraph
import com.zorbi.core.CalibrationRepository
import com.zorbi.core.EngineController
import com.zorbi.core.InputInjector
import com.zorbi.core.ProfileRepository
import com.zorbi.core.TelemetrySink
import com.zorbi.core.TrackerRegistry
import com.zorbi.engine.RealEngineController
import com.zorbi.fakes.p1.FakeCalibrationRepository
import com.zorbi.fakes.p1.FakeTrackerRegistry
import com.zorbi.fakes.p2.FakeProfileRepository
import com.zorbi.fakes.p4.FakeTelemetrySink
import com.zorbi.input.RealInputInjector

/**
 * Production dependency graph wiring live components for game execution and touch injection.
 * Enforces Phase 4 requirements: uses real RealInputInjector and RealEngineController in production.
 */
class RealAppGraph(
    override val trackers: TrackerRegistry,
    override val engine: EngineController,
    override val profiles: ProfileRepository,
    override val calibration: CalibrationRepository,
    override val telemetry: TelemetrySink,
    override val injector: InputInjector
) : AppGraph {

    companion object {
        private const val TAG = "RealAppGraph"

        fun createDefault(context: Context): RealAppGraph {
            Log.i(TAG, "Initializing RealAppGraph with real InputInjector and EngineController")
            val realInjector = RealInputInjector()
            val realEngine = RealEngineController(context)
            val profileRepo = FakeProfileRepository()
            val calibRepo = FakeCalibrationRepository()
            val trackerRegistry = FakeTrackerRegistry()
            val telemetrySink = FakeTelemetrySink()

            return RealAppGraph(
                trackers = trackerRegistry,
                engine = realEngine,
                profiles = profileRepo,
                calibration = calibRepo,
                telemetry = telemetrySink,
                injector = realInjector
            )
        }
    }
}
