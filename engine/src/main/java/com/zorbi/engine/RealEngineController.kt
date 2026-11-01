package com.zorbi.engine

import android.content.Context
import com.zorbi.core.EngineController
import com.zorbi.core.EngineState
import com.zorbi.core.HudFrame
import com.zorbi.core.PerfMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Production implementation of [EngineController] managing Zorbi's execution state
 * and coordinating with the foreground [ZorbiEngineService].
 */
class RealEngineController(
    private val context: Context
) : EngineController {

    private val _state = MutableStateFlow(EngineState.IDLE)
    override val state: StateFlow<EngineState> = _state

    private val _hud = MutableSharedFlow<HudFrame>(extraBufferCapacity = 64)
    override val hud: SharedFlow<HudFrame> = _hud

    var activePackage: String? = null
        private set

    var activePerf: PerfMode? = null
        private set

    override suspend fun start(packageName: String, perf: PerfMode) {
        activePackage = packageName
        activePerf = perf
        _state.value = EngineState.RUNNING

        // Start ZorbiEngineService as a persistent foreground service
        ZorbiEngineService.start(context, packageName)
    }

    override fun pause() {
        if (_state.value == EngineState.RUNNING) {
            _state.value = EngineState.PAUSED
        }
    }

    override fun resume() {
        if (_state.value == EngineState.PAUSED) {
            _state.value = EngineState.RUNNING
        }
    }

    override suspend fun stop() {
        _state.value = EngineState.IDLE
        activePackage = null
        activePerf = null

        // Stop foreground service
        ZorbiEngineService.stop(context)
    }

    suspend fun emitHud(frame: HudFrame) {
        _hud.emit(frame)
    }
}
