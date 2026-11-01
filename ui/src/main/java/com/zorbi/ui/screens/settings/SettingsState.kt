package com.zorbi.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import com.zorbi.core.PerfMode
import java.util.UUID

/**
 * Settings data model matching FROZEN Section 5.5 DataStore keys.
 */
data class ZorbiSettings(
    val perfMode: PerfMode = PerfMode.BALANCED,
    val telemetryOptIn: Boolean = false,
    val deviceId: String = "",
    val wizardDone: Boolean = false,
    val lastGame: String = "",
    val musicVolume: Float = 75f,
    val sfxVolume: Float = 90f,
    val tipsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val language: String = "English",
    val targetFps: String = "60 FPS",
    val difficulty: String = "EASY",
    val steeringDeadzone: Float = 8f,
    val swipeSensitivity: Float = 1.2f,
    val hudSkeletonColor: String = "CYAN NEON"
) {
    companion object {
        const val PREFS_NAME = "zorbi_preferences"
        const val KEY_PERF_MODE = "perf_mode"
        const val KEY_TELEMETRY_OPT_IN = "telemetry_opt_in"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_WIZARD_DONE = "wizard_done"
        const val KEY_LAST_GAME = "last_game"
        const val KEY_MUSIC_VOLUME = "music_volume"
        const val KEY_SFX_VOLUME = "sfx_volume"
        const val KEY_TIPS_ENABLED = "tips_enabled"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_LANGUAGE = "language"
        const val KEY_TARGET_FPS = "target_fps"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_STEERING_DEADZONE = "steering_deadzone"
        const val KEY_SWIPE_SENSITIVITY = "swipe_sensitivity"
        const val KEY_HUD_COLOR = "hud_color"

        val DEFAULT = ZorbiSettings(
            perfMode = PerfMode.BALANCED,
            telemetryOptIn = false,
            deviceId = "",
            wizardDone = false,
            lastGame = "",
            musicVolume = 75f,
            sfxVolume = 90f,
            tipsEnabled = true,
            vibrationEnabled = true,
            language = "English",
            targetFps = "60 FPS",
            difficulty = "EASY",
            steeringDeadzone = 8f,
            swipeSensitivity = 1.2f,
            hudSkeletonColor = "CYAN NEON"
        )
    }
}

/**
 * Persistence manager for Zorbi preferences adhering to Section 5.5.
 * Strictly enforces that telemetry is false by default and requires explicit opt-in.
 */
class SettingsStorage(private val prefs: SharedPreferences) {

    fun load(): ZorbiSettings {
        val perfModeStr = prefs.getString(ZorbiSettings.KEY_PERF_MODE, PerfMode.BALANCED.name)
        val perfMode = try {
            PerfMode.valueOf(perfModeStr ?: PerfMode.BALANCED.name)
        } catch (e: Exception) {
            PerfMode.BALANCED
        }

        val telemetryOptIn = prefs.getBoolean(ZorbiSettings.KEY_TELEMETRY_OPT_IN, false)
        var deviceId = prefs.getString(ZorbiSettings.KEY_DEVICE_ID, null)
        if (deviceId.isNullOrEmpty()) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(ZorbiSettings.KEY_DEVICE_ID, deviceId).apply()
        }

        val wizardDone = prefs.getBoolean(ZorbiSettings.KEY_WIZARD_DONE, false)
        val lastGame = prefs.getString(ZorbiSettings.KEY_LAST_GAME, "") ?: ""
        val musicVolume = prefs.getFloat(ZorbiSettings.KEY_MUSIC_VOLUME, 75f)
        val sfxVolume = prefs.getFloat(ZorbiSettings.KEY_SFX_VOLUME, 90f)
        val tipsEnabled = prefs.getBoolean(ZorbiSettings.KEY_TIPS_ENABLED, true)
        val vibrationEnabled = prefs.getBoolean(ZorbiSettings.KEY_VIBRATION_ENABLED, true)
        val language = prefs.getString(ZorbiSettings.KEY_LANGUAGE, "English") ?: "English"
        val targetFps = prefs.getString(ZorbiSettings.KEY_TARGET_FPS, "60 FPS") ?: "60 FPS"
        val difficulty = prefs.getString(ZorbiSettings.KEY_DIFFICULTY, "EASY") ?: "EASY"
        val steeringDeadzone = prefs.getFloat(ZorbiSettings.KEY_STEERING_DEADZONE, 8f)
        val swipeSensitivity = prefs.getFloat(ZorbiSettings.KEY_SWIPE_SENSITIVITY, 1.2f)
        val hudSkeletonColor = prefs.getString(ZorbiSettings.KEY_HUD_COLOR, "CYAN NEON") ?: "CYAN NEON"

        return ZorbiSettings(
            perfMode = perfMode,
            telemetryOptIn = telemetryOptIn,
            deviceId = deviceId,
            wizardDone = wizardDone,
            lastGame = lastGame,
            musicVolume = musicVolume,
            sfxVolume = sfxVolume,
            tipsEnabled = tipsEnabled,
            vibrationEnabled = vibrationEnabled,
            language = language,
            targetFps = targetFps,
            difficulty = difficulty,
            steeringDeadzone = steeringDeadzone,
            swipeSensitivity = swipeSensitivity,
            hudSkeletonColor = hudSkeletonColor
        )
    }

    fun savePerfMode(mode: PerfMode) {
        prefs.edit().putString(ZorbiSettings.KEY_PERF_MODE, mode.name).apply()
    }

    fun saveTelemetryOptIn(optIn: Boolean) {
        prefs.edit().putBoolean(ZorbiSettings.KEY_TELEMETRY_OPT_IN, optIn).apply()
    }

    fun saveWizardDone(done: Boolean) {
        prefs.edit().putBoolean(ZorbiSettings.KEY_WIZARD_DONE, done).apply()
    }

    fun saveLastGame(packageName: String) {
        prefs.edit().putString(ZorbiSettings.KEY_LAST_GAME, packageName).apply()
    }

    fun saveAll(settings: ZorbiSettings) {
        prefs.edit()
            .putString(ZorbiSettings.KEY_PERF_MODE, settings.perfMode.name)
            .putBoolean(ZorbiSettings.KEY_TELEMETRY_OPT_IN, settings.telemetryOptIn)
            .putFloat(ZorbiSettings.KEY_MUSIC_VOLUME, settings.musicVolume)
            .putFloat(ZorbiSettings.KEY_SFX_VOLUME, settings.sfxVolume)
            .putBoolean(ZorbiSettings.KEY_TIPS_ENABLED, settings.tipsEnabled)
            .putBoolean(ZorbiSettings.KEY_VIBRATION_ENABLED, settings.vibrationEnabled)
            .putString(ZorbiSettings.KEY_LANGUAGE, settings.language)
            .putString(ZorbiSettings.KEY_TARGET_FPS, settings.targetFps)
            .putString(ZorbiSettings.KEY_DIFFICULTY, settings.difficulty)
            .putFloat(ZorbiSettings.KEY_STEERING_DEADZONE, settings.steeringDeadzone)
            .putFloat(ZorbiSettings.KEY_SWIPE_SENSITIVITY, settings.swipeSensitivity)
            .putString(ZorbiSettings.KEY_HUD_COLOR, settings.hudSkeletonColor)
            .apply()
    }

    fun resetToDefaults(): ZorbiSettings {
        val existingDeviceId = prefs.getString(ZorbiSettings.KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString()

        prefs.edit()
            .putString(ZorbiSettings.KEY_PERF_MODE, PerfMode.BALANCED.name)
            .putBoolean(ZorbiSettings.KEY_TELEMETRY_OPT_IN, false)
            .putString(ZorbiSettings.KEY_DEVICE_ID, existingDeviceId)
            .putBoolean(ZorbiSettings.KEY_WIZARD_DONE, false)
            .putString(ZorbiSettings.KEY_LAST_GAME, "")
            .putFloat(ZorbiSettings.KEY_MUSIC_VOLUME, 75f)
            .putFloat(ZorbiSettings.KEY_SFX_VOLUME, 90f)
            .putBoolean(ZorbiSettings.KEY_TIPS_ENABLED, true)
            .putBoolean(ZorbiSettings.KEY_VIBRATION_ENABLED, true)
            .putString(ZorbiSettings.KEY_LANGUAGE, "English")
            .putString(ZorbiSettings.KEY_TARGET_FPS, "60 FPS")
            .putString(ZorbiSettings.KEY_DIFFICULTY, "EASY")
            .putFloat(ZorbiSettings.KEY_STEERING_DEADZONE, 8f)
            .putFloat(ZorbiSettings.KEY_SWIPE_SENSITIVITY, 1.2f)
            .putString(ZorbiSettings.KEY_HUD_COLOR, "CYAN NEON")
            .apply()

        return ZorbiSettings.DEFAULT.copy(deviceId = existingDeviceId)
    }

    companion object {
        fun fromContext(context: Context): SettingsStorage {
            val sp = context.getSharedPreferences(ZorbiSettings.PREFS_NAME, Context.MODE_PRIVATE)
            return SettingsStorage(sp)
        }
    }
}
