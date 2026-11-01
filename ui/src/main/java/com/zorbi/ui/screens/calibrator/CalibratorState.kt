package com.zorbi.ui.screens.calibrator

import com.zorbi.core.Calibration
import com.zorbi.core.TrackerFrame

enum class CalibrationPhase(val title: String, val instruction: String) {
    READY("Ready", "Position your phone securely and face the front camera."),
    DETECTING("Aligning", "Hold your hand upright and clear in front of the camera."),
    RECORDING_NEUTRAL("Neutral Center", "Keep your hand/wrist in your natural, straight rest position."),
    RECORDING_RANGE("Range of Motion", "Tilt and move through your comfortable left/right and up/down span."),
    COMPUTING("Analyzing", "Calculating personalized deadzones, reach scales, and angles..."),
    SUCCESS("Complete", "Motion profile calibrated! Ready for touch injection."),
    ERROR("Tracking Lost", "Insufficient tracking samples detected. Please try again.")
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
