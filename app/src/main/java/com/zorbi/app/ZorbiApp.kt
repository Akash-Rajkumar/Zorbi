package com.zorbi.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.zorbi.app.adapter.SupabaseRemoteProfileSourceAdapter
import com.zorbi.app.filter.OneEuroFrameFilter
import com.zorbi.calib.RealCalibrationRepository
import com.zorbi.calib.fist.FistDetector
import com.zorbi.core.AppGraph
import com.zorbi.data.SupabaseConfig
import com.zorbi.data.SupabaseHttpClient
import com.zorbi.data.SupabaseRemoteProfileSource
import com.zorbi.data.SupabaseTelemetrySink
import com.zorbi.engine.RealEngineController
import com.zorbi.engine.RealProfileRepository
import com.zorbi.engine.ZorbiEngineService
import com.zorbi.input.RealInputInjector
import com.zorbi.vision.RealTrackerRegistry

class ZorbiApp : Application() {

    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initAppGraph()
    }

    private fun initAppGraph() {
        val trackerRegistry = RealTrackerRegistry(this)
        val calibrationRepo = RealCalibrationRepository(this)
        val inputInjector = RealInputInjector()
        inputInjector.onDispatchResult = { success, msg ->
            com.zorbi.gesture.GestureDiagnostics.setLastInjection(msg)
        }

        val supabaseConfig = SupabaseConfig.fromValues(
            System.getenv("SUPABASE_URL"),
            System.getenv("SUPABASE_ANON_KEY")
        )
        val httpClient = SupabaseHttpClient(supabaseConfig)
        val dataRemoteSource = SupabaseRemoteProfileSource(httpClient)
        val engineRemoteSource = SupabaseRemoteProfileSourceAdapter(dataRemoteSource)

        val profileRepo = RealProfileRepository(
            context = this,
            remoteSource = engineRemoteSource
        )
        val telemetrySink = SupabaseTelemetrySink(httpClient)

        val frameFilter = OneEuroFrameFilter()
        val fistDetector = FistDetector()

        val engine = RealEngineController(
            trackerRegistry = trackerRegistry,
            profileRepository = profileRepo,
            calibrationRepository = calibrationRepo,
            inputInjector = inputInjector,
            filter = frameFilter,
            fistDetector = fistDetector,
            telemetrySink = telemetrySink
        )

        appGraph = RealAppGraph(
            trackers = trackerRegistry,
            engine = engine,
            profiles = profileRepo,
            calibration = calibrationRepo,
            telemetry = telemetrySink,
            injector = inputInjector
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ZorbiEngineService.CHANNEL_ID,
                "Zorbi Engine Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running gesture processing and camera analysis"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
