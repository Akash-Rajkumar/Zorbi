package com.zorbi.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.zorbi.core.AppGraph
import com.zorbi.engine.ZorbiEngineService

class ZorbiApp : Application() {

    sealed interface AppGraphState {
        data object Loading : AppGraphState
        data class Ready(val graph: AppGraph) : AppGraphState
        data class Error(val cause: Throwable) : AppGraphState
    }

    var graphState: AppGraphState = AppGraphState.Loading
        private set

    lateinit var appGraph: AppGraph
        private set

    /**
     * Resolves the AppGraph instance.
     * In production, returns the wired RealAppGraph initialized in onCreate().
     * If not initialized, cleanly falls back to FakeAppGraph for testing.
     */
    fun getGraph(): AppGraph {
        return when (val state = graphState) {
            is AppGraphState.Ready -> state.graph
            is AppGraphState.Error -> throw IllegalStateException("RealAppGraph initialization failed", state.cause)
            is AppGraphState.Loading -> {
                if (::appGraph.isInitialized) {
                    appGraph
                } else {
                    try {
                        val fakeClass = Class.forName("com.zorbi.fakes.FakeAppGraph")
                        fakeClass.getDeclaredConstructor().newInstance() as AppGraph
                    } catch (e: Exception) {
                        throw IllegalStateException("AppGraph not initialized and FakeAppGraph unavailable", e)
                    }
                }
            }
        }
    }

    fun initGraph() {
        Log.i(TAG_STARTUP, "RealAppGraph initialization started")
        try {
            val graph = RealAppGraph.createDefault(this)
            appGraph = graph
            graphState = AppGraphState.Ready(graph)
            Log.i(TAG_STARTUP, "RealAppGraph initialization completed successfully")
        } catch (t: Throwable) {
            Log.e(TAG_STARTUP, "RealAppGraph initialization FAILED with exception", t)
            graphState = AppGraphState.Error(t)
        }
    }

    fun retryInitialization() {
        Log.i(TAG_STARTUP, "Retrying RealAppGraph initialization...")
        initGraph()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG_STARTUP, "Application onCreate started [package=${packageName}]")
        createNotificationChannel()
        com.zorbi.ui.util.ZorbiVoiceCoach.init(this)
        initGraph()
        Log.i(TAG_STARTUP, "Application onCreate completed")
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
            Log.i(TAG_STARTUP, "Notification channel created: ${ZorbiEngineService.CHANNEL_ID}")
        }
    }

    companion object {
        const val TAG_STARTUP = "ZORBI_STARTUP"

        var instance: ZorbiApp? = null
            private set
    }
}
