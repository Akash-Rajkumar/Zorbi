package com.zorbi.ui.navigation

// FROZEN Navigation Routes per Section 5.4
object NavRoutes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val WIZARD = "wizard"
    const val CALIBRATE = "calibrate/{packageName}"
    const val PLAY = "play/{packageName}"
    const val COMMUNITY = "community"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun calibrate(packageName: String): String = "calibrate/$packageName"
    fun play(packageName: String): String = "play/$packageName"
}

