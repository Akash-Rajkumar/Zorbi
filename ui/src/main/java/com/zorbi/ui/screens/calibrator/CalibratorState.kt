package com.zorbi.ui.screens.calibrator

import com.zorbi.core.Calibration
import com.zorbi.core.TrackerFrame

enum class CalibrationPhase(val title: String, val instruction: String) {
    READY("Ready", "Put your phone on a table facing you."),
    DETECTING("Finding Hand", "Show your open hand to the front camera."),
    RECORDING_NEUTRAL("Center Hand", "Hold your hand still in the center of the screen."),
    RECORDING_RANGE("Move Hand", "Slowly move your hand left, right, up, and down."),
    COMPUTING("Saving", "Saving your motion controls..."),
    SUCCESS("All Done!", "Calibration complete! Tap below to start playing."),
    ERROR("Hand Lost", "We lost sight of your hand. Please try again with good lighting.")
}

data class CalibratorUiState(
    val phase: CalibrationPhase = CalibrationPhase.READY,
    val secondsRemaining: Int = 60,
    val isTrackingLocked: Boolean = false,
    val sampleCount: Int = 0,
    val lastFrame: TrackerFrame? = null,
    val result: Calibration? = null,
    val errorMessage: String? = null
)
