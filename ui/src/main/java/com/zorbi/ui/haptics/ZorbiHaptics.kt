package com.zorbi.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Interface defining tactical haptic feedback events in Zorbi.
 * Adheres strictly to low-latency, non-intrusive feedback that never
 * interferes with gameplay or gesture recognition.
 */
interface ZorbiHaptics {
    /** Subtle tap for standard buttons, tabs, and filters. */
    fun click()

    /** Affirmative confirmation for successful import, calibration save, or permission grant. */
    fun confirm()

    /** Warning double-pulse for errors, denied permissions, or lost tracking. */
    fun warning()

    /** Fine tick for countdown timer seconds, steering dial changes, or slider steps. */
    fun tick()

    /** State change tick for toggles and switches. */
    fun toggle()
}

/**
 * Implementation of ZorbiHaptics using Android View and Vibrator APIs.
 */
class AndroidZorbiHaptics(
    private val view: View?,
    private val context: Context?
) : ZorbiHaptics {

    private val vibrator: Vibrator? by lazy {
        try {
            if (context != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun click() {
        try {
            if (view != null) {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            }
        } catch (ignored: Exception) {}
    }

    override fun confirm() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && view != null) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (view != null) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } catch (ignored: Exception) {}
    }

    override fun warning() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && view != null) {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
                // Short double-pulse: 20ms on, 60ms off, 20ms on
                val timings = longArrayOf(0, 25, 60, 25)
                val amplitudes = intArrayOf(0, 180, 0, 180)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else if (view != null) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } catch (ignored: Exception) {}
    }

    override fun tick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && view != null) {
                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && view != null) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            }
        } catch (ignored: Exception) {}
    }

    override fun toggle() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && view != null) {
                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
            } else {
                click()
            }
        } catch (ignored: Exception) {}
    }
}

/**
 * No-op implementation for preview environments and unit tests without Android view system.
 */
object NoOpZorbiHaptics : ZorbiHaptics {
    override fun click() {}
    override fun confirm() {}
    override fun warning() {}
    override fun tick() {}
    override fun toggle() {}
}

/**
 * Composable remember function providing lifecycle-aware access to ZorbiHaptics.
 */
@Composable
fun rememberZorbiHaptics(): ZorbiHaptics {
    val view = LocalView.current
    val context = LocalContext.current

    return remember(view, context) {
        AndroidZorbiHaptics(view, context)
    }
}
