package com.zorbi.gesture

import com.zorbi.core.GestureInterpreter

/**
 * Internal interface extending [GestureInterpreter] that allows the engine pipeline
 * to explicitly reset gesture tracking history, cooldown timers, and sliding windows
 * upon pause, resume, or session termination without modifying frozen `:core` contracts.
 */
interface ResettableInterpreter : GestureInterpreter {
    fun reset()
}

