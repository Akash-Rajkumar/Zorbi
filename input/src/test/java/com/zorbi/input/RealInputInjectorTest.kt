package com.zorbi.input

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Pt
import org.junit.Test

class RealInputInjectorTest {

    @Test
    fun testIsReady_reflectsServiceConnectionState() {
        // By default in unit test environment, ZorbiAccessibilityService is not connected
        val injector = RealInputInjector(
            serviceProvider = { null },
            displayBoundsProvider = { Pair(1080, 2400) }
        )

        assertThat(injector.isReady.value).isFalse()
    }

    @Test
    fun testDisconnectedService_methodsExecuteSafelyWithoutCrashing() {
        val injector = RealInputInjector(
            serviceProvider = { null },
            displayBoundsProvider = { Pair(1080, 2400) }
        )

        // All operations must safely early-exit without crashing when service is disconnected
        injector.tap(Pt(0.5f, 0.5f))
        injector.swipe(Pt(0.1f, 0.1f), Pt(0.9f, 0.9f), 150L)
        injector.path(listOf(Pt(0.1f, 0.1f), Pt(0.5f, 0.5f), Pt(0.9f, 0.9f)), 200L)
        injector.holdAt(Pt(0.5f, 0.5f))
        injector.dragTo(Pt(0.6f, 0.6f))
        injector.release()

        assertThat(injector.isReady.value).isFalse()
    }

    @Test
    fun testPath_emptyList_safeNoOp() {
        val injector = RealInputInjector(
            serviceProvider = { null },
            displayBoundsProvider = { Pair(1080, 2400) }
        )

        // Should not throw or crash on empty list
        injector.path(emptyList(), 100L)
    }

    @Test
    fun testInterruptionHandler_resetsStateSafely() {
        val injector = RealInputInjector(
            serviceProvider = { null },
            displayBoundsProvider = { Pair(1080, 2400) }
        )

        // Trigger interruption callback
        ZorbiAccessibilityService._onInterrupted?.invoke()

        // Injector remains stable
        injector.release()
        assertThat(injector.isReady.value).isFalse()
    }
}

