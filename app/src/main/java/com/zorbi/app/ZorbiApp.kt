package com.zorbi.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.zorbi.core.AppGraph
import com.zorbi.engine.ZorbiEngineService

class ZorbiApp : Application() {

    lateinit var appGraph: AppGraph
        private set

    /**
     * Resolves the AppGraph instance.
     * In production, returns the wired RealAppGraph initialized in onCreate().
     * If not initialized, cleanly falls back to FakeAppGraph for testing.
     */
    fun getGraph(): AppGraph {
        if (::appGraph.isInitialized) {
            return appGraph
        }
        return try {
            val fakeClass = Class.forName("com.zorbi.fakes.FakeAppGraph")
            fakeClass.getDeclaredConstructor().newInstance() as AppGraph
        } catch (e: Exception) {
            throw IllegalStateException("AppGraph not initialized and FakeAppGraph unavailable", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Phase 4: Wire live RealAppGraph containing real input injector and engine controller
        appGraph = RealAppGraph.createDefault(this)
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
