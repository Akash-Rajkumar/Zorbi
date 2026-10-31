package com.zorbi.input

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ZorbiAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event processing placeholder
    }

    override fun onInterrupt() {
        // Interrupt placeholder
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isConnected.value = false
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected

        var instance: ZorbiAccessibilityService? = null
            private set
    }
}

