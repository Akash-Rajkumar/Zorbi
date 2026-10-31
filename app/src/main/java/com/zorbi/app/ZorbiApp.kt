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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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

