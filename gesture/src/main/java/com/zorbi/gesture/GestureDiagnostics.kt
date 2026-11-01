package com.zorbi.gesture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GestureDiagData(
    val handDetected: Boolean = false,
    val handSide: String = "None",
    val handScore: Float = 0f,
    val wristX: Float = 0f,
    val wristY: Float = 0f,
    val middleMcpX: Float = 0f,
    val middleMcpY: Float = 0f,
    val palmX: Float = 0f,
    val palmY: Float = 0f,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f,
    val distance: Float = 0f,
    val speed: Float = 0f,
    val minDistance: Float = 0.08f,
    val minSpeed: Float = 0.35f,
    val windowDurationMs: Long = 0L,
    val sampleCount: Int = 0,
    val isArmed: Boolean = true,
    val candidateDir: String = "None",
    val status: String = "Idle",
    val lastRecognizedGesture: String = "None",
    val lastRecognizedGestureTs: Long = 0L,
    val lastInjectionResult: String = "None"
)

object GestureDiagnostics {
    private val _data = MutableStateFlow(GestureDiagData())
    val data: StateFlow<GestureDiagData> = _data.asStateFlow()

    fun update(transform: (GestureDiagData) -> GestureDiagData) {
        _data.value = transform(_data.value)
    }

    fun setLastInjection(result: String) {
        _data.value = _data.value.copy(lastInjectionResult = result)
    }
}

