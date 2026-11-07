package com.zorbi.ui.screens.wizard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * State representing each prerequisite required for Zorbi to function.
 */
data class WizardState(
    val isAccessibilityGranted: Boolean = false,
    val isCameraGranted: Boolean = false,
    val isNotificationGranted: Boolean = false,
    val isBatteryOptimized: Boolean = false
) {
    val totalSteps: Int = 4

    val grantedCount: Int
        get() = (if (isAccessibilityGranted) 1 else 0) +
                (if (isCameraGranted) 1 else 0) +
                (if (isNotificationGranted) 1 else 0) +
                (if (isBatteryOptimized) 1 else 0)

    val isAllGreen: Boolean
        get() = isAccessibilityGranted && isCameraGranted && isNotificationGranted && isBatteryOptimized
}

/**
 * Helper to query system status and launch relevant settings intents.
 */
object WizardSystemHelper {

    fun checkStatus(context: Context, isInjectorReady: Boolean): WizardState {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Automatic prior to Android 13
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryExempt = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        return WizardState(
            isAccessibilityGranted = isInjectorReady,
            isCameraGranted = hasCamera,
            isNotificationGranted = hasNotification,
            isBatteryOptimized = isBatteryExempt
        )
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = try {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }

    fun isVivoDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("vivo") || brand.contains("vivo") ||
                manufacturer.contains("iqoo") || brand.contains("iqoo")
    }
}
