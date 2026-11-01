package com.zorbi.engine

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ZorbiEngineService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "ZorbiEngineService received ACTION_STOP")
            stopForegroundService()
            return START_NOT_STICKY
        }

        Log.i(TAG, "ZorbiEngineService starting foreground service")
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zorbi Engine")
            .setContentText("Gesture tracking and camera processing active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
            Log.i(TAG, "ZorbiEngineService started foreground with type $foregroundType")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }

        return START_STICKY
    }

    private fun stopForegroundService() {
        _isRunning.value = false
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        Log.i(TAG, "ZorbiEngineService destroying")
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }
    }

    companion object {
        private const val TAG = "ZorbiEngineService"
        const val CHANNEL_ID = "zorbi_engine"
        const val NOTIFICATION_ID = 2026

        const val ACTION_START = "com.zorbi.engine.START"
        const val ACTION_STOP = "com.zorbi.engine.STOP"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        fun start(context: Context, packageName: String? = null) {
            val intent = Intent(context, ZorbiEngineService::class.java).apply {
                action = ACTION_START
                if (packageName != null) putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ZorbiEngineService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }
}
