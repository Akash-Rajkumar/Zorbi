package com.zorbi.fakes.p2

import com.zorbi.core.InputInjector
import com.zorbi.core.Pt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeInputInjector : InputInjector {
    private val _isReady = MutableStateFlow(true)
    override val isReady: StateFlow<Boolean> = _isReady

    val recordedActions = mutableListOf<String>()

    fun setReady(ready: Boolean) {
        _isReady.value = ready
    }

    override fun tap(p: Pt) {
        recordedActions.add("tap(${p.x}, ${p.y})")
    }

    override fun swipe(from: Pt, to: Pt, durationMs: Long) {
        recordedActions.add("swipe(from=(${from.x}, ${from.y}), to=(${to.x}, ${to.y}), duration=$durationMs)")
    }

    override fun path(points: List<Pt>, durationMs: Long) {
        recordedActions.add("path(points=${points.size}, duration=$durationMs)")
    }

    override fun holdAt(p: Pt) {
        recordedActions.add("holdAt(${p.x}, ${p.y})")
    }

    override fun dragTo(p: Pt) {
        recordedActions.add("dragTo(${p.x}, ${p.y})")
    }

    override fun release() {
        recordedActions.add("release")
    }
}

