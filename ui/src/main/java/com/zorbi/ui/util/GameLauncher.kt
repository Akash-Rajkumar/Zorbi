package com.zorbi.ui.util

import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Result of attempting to launch an external game.
 */
sealed interface GameLaunchResult {
    data object Success : GameLaunchResult
    data class NotInstalled(val packageName: String) : GameLaunchResult
    data class Error(val message: String) : GameLaunchResult
}

/**
 * Utility responsible for resolving and launching external game activities via PackageManager.
 * Enforces Phase 1 requirement: real game execution without mock Screen substitution.
 */
object GameLauncher {

    /**
     * Checks if the package has a valid launcher intent on the device.
     */
    fun isGameInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launches the game activity directly into the foreground if installed.
     * Displays a useful Toast and returns [GameLaunchResult.NotInstalled] if unavailable.
     */
    fun launchGame(
        context: Context,
        packageName: String,
        showToastOnError: Boolean = true
    ): GameLaunchResult {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                GameLaunchResult.Success
            } else {
                if (showToastOnError) {
                    Toast.makeText(
                        context,
                        "Game '$packageName' is not installed. Please install it from Google Play Store.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                GameLaunchResult.NotInstalled(packageName)
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Failed to launch $packageName"
            if (showToastOnError) {
                Toast.makeText(context, "Error launching game: $errorMsg", Toast.LENGTH_LONG).show()
            }
            GameLaunchResult.Error(errorMsg)
        }
    }
}
