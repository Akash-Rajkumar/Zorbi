package com.zorbi.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Foreground Service hosting the Zorbi vision, gesture processing, and input injection engine.
 *
 * Implements:
 * 1. Android 14 Foreground Service lifecycle with [ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA].
 * 2. Persistent ongoing notification keeping tracking alive while external games are in the foreground.
 * 3. Safe start and stop commands with graceful teardown.
 */
class ZorbiEngineService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        _isRunning.value = true
        Log.i(TAG, "ZorbiEngineService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Received ACTION_STOP, stopping foreground engine")
            stopForegroundService()
            return START_NOT_STICKY
        }

        val targetPkg = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: "Gaming Session"
        val notification = buildForegroundNotification(targetPkg)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(TAG, "ZorbiEngineService running in foreground for: $targetPkg")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun stopForegroundService() {
        _isRunning.value = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        serviceScope.cancel()
        Log.i(TAG, "ZorbiEngineService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZORBI Gesture Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active motion tracking and touch injection"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(targetPackage: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("ZORBI Gesture Engine Active")
            .setContentText("Touchless motion tracking running for $targetPackage")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
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

        /**
         * Safe helper to start the foreground engine service.
         */
        fun start(context: Context, packageName: String? = null) {
            val intent = Intent(context, ZorbiEngineService::class.java).apply {
                action = ACTION_START
                if (packageName != null) putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not start ZorbiEngineService", e)
            }
        }

        /**
         * Safe helper to stop the engine service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, ZorbiEngineService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not stop ZorbiEngineService", e)
            }
        }
    }
}
