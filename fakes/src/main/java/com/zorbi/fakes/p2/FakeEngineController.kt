package com.zorbi.fakes.p2

import com.zorbi.core.EngineController
import com.zorbi.core.EngineState
import com.zorbi.core.HudFrame
import com.zorbi.core.PerfMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class FakeEngineController : EngineController {
    private val _state = MutableStateFlow(EngineState.IDLE)
    override val state: StateFlow<EngineState> = _state

    private val _hud = MutableSharedFlow<HudFrame>()
    override val hud: SharedFlow<HudFrame> = _hud

    var activePackage: String? = null
        private set
    var activePerf: PerfMode? = null
        private set

    override suspend fun start(packageName: String, perf: PerfMode) {
        activePackage = packageName
        activePerf = perf
        _state.value = EngineState.RUNNING
    }

    override fun pause() {
        _state.value = EngineState.PAUSED
    }

    override fun resume() {
        _state.value = EngineState.RUNNING
    }

    override suspend fun stop() {
        _state.value = EngineState.IDLE
        activePackage = null
        activePerf = null
    }

    suspend fun emitHud(frame: HudFrame) {
        _hud.emit(frame)
    }

    fun setState(newState: EngineState) {
        _state.value = newState
    }
}

