package com.zorbi.app

import android.content.Context
import android.util.Log
import com.zorbi.calib.RealCalibrationRepository
import com.zorbi.core.AppGraph
import com.zorbi.core.CalibrationRepository
import com.zorbi.core.EngineController
import com.zorbi.core.InputInjector
import com.zorbi.core.ProfileRepository
import com.zorbi.core.TelemetrySink
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerRegistry
import com.zorbi.engine.RealEngineController
import com.zorbi.engine.RealProfileRepository
import com.zorbi.data.SupabaseConfig
import com.zorbi.data.SupabaseHttpClient
import com.zorbi.data.SupabaseTelemetrySink
import com.zorbi.input.RealInputInjector
import com.zorbi.vision.HandDualWheelTracker
import com.zorbi.vision.HandSingleTracker
import com.zorbi.vision.PoseLiteTracker
import java.util.concurrent.ConcurrentHashMap

/**
 * Production implementation of [TrackerRegistry] resolving real on-device MediaPipe CameraX trackers.
 */
class RealTrackerRegistry(private val context: Context) : TrackerRegistry {
    private val instances = ConcurrentHashMap<TrackerId, Tracker>()

    override fun get(id: TrackerId): Tracker = instances.getOrPut(id) {
        when (id) {
            TrackerId.HAND_SINGLE -> HandSingleTracker(context)
            TrackerId.HAND_DUAL_WHEEL -> HandDualWheelTracker(context)
            TrackerId.POSE_LITE -> PoseLiteTracker(context)
        }
    }
}

/**
 * Production dependency graph wiring live components for game execution, camera vision tracking,
 * touch injection, and persistent calibration.
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
            Log.i(TAG, "Initializing RealAppGraph with real Vision, Input, Engine, and Calibration backends")
            val realInjector = RealInputInjector()
            val profileRepo = RealProfileRepository(context)
            val calibRepo = RealCalibrationRepository(context)
            val trackerRegistry = RealTrackerRegistry(context)
            val prefs = context.getSharedPreferences("zorbi_settings", Context.MODE_PRIVATE)
            val telemetrySink = SupabaseTelemetrySink(
                httpClient = SupabaseHttpClient(SupabaseConfig.UNCONFIGURED),
                consentProvider = { prefs.getBoolean("telemetry_opt_in", true) }
            )
            val realEngine = RealEngineController(
                trackerRegistry = trackerRegistry,
                profileRepository = profileRepo,
                calibrationRepository = calibRepo,
                inputInjector = realInjector,
                telemetrySink = telemetrySink,
                context = context
            )

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
