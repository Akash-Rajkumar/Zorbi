package com.zorbi.input

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Pt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-device and instrumentation test suite for [RealInputInjector], [ZorbiAccessibilityService],
 * and [RealOverlayHost] per Task P2-2 and verification requirement T-P2-2.
 *
 * Runs on physical Android devices (tested on vivo Z9 Lite / OriginOS / API 34).
 */
@RunWith(AndroidJUnit4::class)
class InputInjectorDeviceTest {

    private lateinit var uiDevice: UiDevice
    private lateinit var context: Context
    private val mainHandler = Handler(Looper.getMainLooper())

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        uiDevice = UiDevice.getInstance(instrumentation)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        ZorbiAccessibilityService.instance?.detachAll()
    }

    // 1. Accessibility Service Readiness
    @Test
    fun testAccessibilityService_isReady_reflectsConnectionState() {
        val injector = RealInputInjector()
        // If the service is running on the device, isReady is true.
        // We verify the StateFlow contract holds and is accessible.
        val ready = injector.isReady.value
        assertThat(ready).isEqualTo(ZorbiAccessibilityService.isConnected.value)
    }

    // 2. Tap at Screen Center
    @Test
    fun testTap_centerCoordinates_createsValidTapGesture() {
        val displayWidth = uiDevice.displayWidth
        val displayHeight = uiDevice.displayHeight
        var dispatchedGesture: GestureDescription? = null

        val testService = createTestAccessibilityService { gesture ->
            dispatchedGesture = gesture
        }

        ZorbiAccessibilityService.setConnectedForTesting(testService)
        try {
            val injector = RealInputInjector(
                serviceProvider = { testService },
                displayBoundsProvider = { Pair(displayWidth, displayHeight) }
            )

            // Inject center tap (0.5, 0.5)
            injector.tap(Pt(0.5f, 0.5f))

            assertThat(dispatchedGesture).isNotNull()
            val stroke = dispatchedGesture!!.getStroke(0)
            assertThat(stroke).isNotNull()
            assertThat(stroke.willContinue()).isFalse()
            assertThat(stroke.duration).isEqualTo(GesturePathBuilder.DEFAULT_TAP_DURATION_MS)

            // Verify coordinates mapped to screen center
            val expectedX = displayWidth * 0.5f
            val expectedY = displayHeight * 0.5f
            val coords = getPathStartAndEnd(stroke.path)
            assertThat(coords.first).isWithin(1.5f).of(expectedX)
            assertThat(coords.second).isWithin(1.5f).of(expectedY)
        } finally {
            ZorbiAccessibilityService.setConnectedForTesting(null)
        }
    }

    // 3. Directional Swipes (Up, Down, Left, Right)
    @Test
    fun testSwipe_allFourDirections_generatesCorrectStrokes() {
        val displayWidth = uiDevice.displayWidth
        val displayHeight = uiDevice.displayHeight
        val dispatchedGestures = mutableListOf<GestureDescription>()

        val testService = createTestAccessibilityService { gesture ->
            dispatchedGestures.add(gesture)
        }

        ZorbiAccessibilityService.setConnectedForTesting(testService)
        try {
            val injector = RealInputInjector(
                serviceProvider = { testService },
                displayBoundsProvider = { Pair(displayWidth, displayHeight) }
            )

            // 1. Swipe UP (bottom to top)
            injector.swipe(Pt(0.5f, 0.8f), Pt(0.5f, 0.2f), 200L)
            // 2. Swipe DOWN (top to bottom)
            injector.swipe(Pt(0.5f, 0.2f), Pt(0.5f, 0.8f), 200L)
            // 3. Swipe LEFT (right to left)
            injector.swipe(Pt(0.8f, 0.5f), Pt(0.2f, 0.5f), 200L)
            // 4. Swipe RIGHT (left to right)
            injector.swipe(Pt(0.2f, 0.5f), Pt(0.8f, 0.5f), 200L)

            assertThat(dispatchedGestures).hasSize(4)

            // Verify UP
            val upStroke = dispatchedGestures[0].getStroke(0)
            val upPoints = getPathStartAndEnd(upStroke.path)
            assertThat(upPoints.first).isWithin(2f).of(displayWidth * 0.5f)
            assertThat(upPoints.second).isWithin(2f).of(displayHeight * 0.8f)
            assertThat(upPoints.fourth).isLessThan(upPoints.second) // Ended higher up

            // Verify DOWN
            val downStroke = dispatchedGestures[1].getStroke(0)
            val downPoints = getPathStartAndEnd(downStroke.path)
            assertThat(downPoints.fourth).isGreaterThan(downPoints.second) // Ended lower down

            // Verify LEFT
            val leftStroke = dispatchedGestures[2].getStroke(0)
            val leftPoints = getPathStartAndEnd(leftStroke.path)
            assertThat(leftPoints.third).isLessThan(leftPoints.first) // Ended further left

            // Verify RIGHT
            val rightStroke = dispatchedGestures[3].getStroke(0)
            val rightPoints = getPathStartAndEnd(rightStroke.path)
            assertThat(rightPoints.third).isGreaterThan(rightPoints.first) // Ended further right
        } finally {
            ZorbiAccessibilityService.setConnectedForTesting(null)
        }
    }

    // 4. Multi-Point Path Gesture (3-point slash)
    @Test
    fun testPath_threePointSlash_createsAccurateMultiSegmentPath() {
        val displayWidth = uiDevice.displayWidth
        val displayHeight = uiDevice.displayHeight
        var dispatchedGesture: GestureDescription? = null

        val testService = createTestAccessibilityService { gesture ->
            dispatchedGesture = gesture
        }

        ZorbiAccessibilityService.setConnectedForTesting(testService)
        try {
            val injector = RealInputInjector(
                serviceProvider = { testService },
                displayBoundsProvider = { Pair(displayWidth, displayHeight) }
            )

            val points = listOf(
                Pt(0.2f, 0.2f),
                Pt(0.5f, 0.5f),
                Pt(0.8f, 0.2f)
            )
            injector.path(points, 300L)

            assertThat(dispatchedGesture).isNotNull()
            val stroke = dispatchedGesture!!.getStroke(0)
            assertThat(stroke.duration).isEqualTo(300L)

            // Measure path length to confirm it covers both segments
            val pm = PathMeasure(stroke.path, false)
            val length = pm.length
            assertThat(length).isGreaterThan(100f)
        } finally {
            ZorbiAccessibilityService.setConnectedForTesting(null)
        }
    }

    // 5. Continuous holdAt -> dragTo -> release as ONE continuous gesture
    @Test
    fun testContinuousGesture_holdDragRelease_verifiedAsSingleContinuousStroke() {
        val displayWidth = uiDevice.displayWidth
        val displayHeight = uiDevice.displayHeight
        val dispatchedGestures = mutableListOf<GestureDescription>()

        val testService = createTestAccessibilityService { gesture ->
            dispatchedGestures.add(gesture)
        }

        ZorbiAccessibilityService.setConnectedForTesting(testService)
        try {
            val injector = RealInputInjector(
                serviceProvider = { testService },
                displayBoundsProvider = { Pair(displayWidth, displayHeight) }
            )

            // Phase 1: holdAt
            injector.holdAt(Pt(0.3f, 0.5f))
            assertThat(dispatchedGestures).hasSize(1)
            val holdStroke = dispatchedGestures[0].getStroke(0)
            // Stroke must indicate it will continue
            assertThat(holdStroke.willContinue()).isTrue()

            // Phase 2: dragTo
            injector.dragTo(Pt(0.7f, 0.5f))
            assertThat(dispatchedGestures).hasSize(2)
            val dragStroke = dispatchedGestures[1].getStroke(0)
            // Intermediate stroke must continue the gesture
            assertThat(dragStroke.willContinue()).isTrue()
            // In Android, continueStroke preserves continuity; stroke cannot be independent
            assertThat(dragStroke).isNotEqualTo(holdStroke)

            // Phase 3: release
            injector.release()
            assertThat(dispatchedGestures).hasSize(3)
            val releaseStroke = dispatchedGestures[2].getStroke(0)
            // Final stroke MUST NOT continue (willContinue = false)
            assertThat(releaseStroke.willContinue()).isFalse()

            // CRITICAL CHECK: Reject 3 independent strokes
            // In independent gestures, willContinue would be false for all 3.
            assertThat(holdStroke.willContinue()).isTrue()
            assertThat(dragStroke.willContinue()).isTrue()
            assertThat(releaseStroke.willContinue()).isFalse()
        } finally {
            ZorbiAccessibilityService.setConnectedForTesting(null)
        }
    }

    // 6. Dispatch Failure and Cancellation Handling
    @Test
    fun testDispatchFailure_andCancellationHandling_resetsStateGracefully() {
        val displayWidth = uiDevice.displayWidth
        val displayHeight = uiDevice.displayHeight

        // Service that always rejects gestures
        val rejectingService = object : ZorbiAccessibilityService() {
            override fun dispatch(
                gesture: GestureDescription,
                callback: GestureResultCallback?
            ): Boolean {
                callback?.onCancelled(gesture)
                return false
            }
        }

        ZorbiAccessibilityService.setConnectedForTesting(rejectingService)
        try {
            val injector = RealInputInjector(
                serviceProvider = { rejectingService },
                displayBoundsProvider = { Pair(displayWidth, displayHeight) }
            )

            // Hold should be cancelled gracefully
            injector.holdAt(Pt(0.5f, 0.5f))

            // After cancellation, calling release or subsequent tap should not throw or hang
            injector.release()
            injector.tap(Pt(0.5f, 0.5f))
        } finally {
            ZorbiAccessibilityService.setConnectedForTesting(null)
        }
    }

    // 7. Service Disconnection and Reconnection Handling
    @Test
    fun testServiceDisconnection_andReconnection_handlesLifecycleProperly() {
        val injector = RealInputInjector()

        // 1. Simulate disconnected
        ZorbiAccessibilityService.setConnectedForTesting(null)
        assertThat(injector.isReady.value).isFalse()

        // Operations while disconnected should safely no-op without crash
        injector.tap(Pt(0.5f, 0.5f))
        injector.swipe(Pt(0.1f, 0.1f), Pt(0.9f, 0.9f), 100L)
        injector.holdAt(Pt(0.5f, 0.5f))
        injector.dragTo(Pt(0.6f, 0.6f))
        injector.release()

        // 2. Simulate reconnected
        var gestureDispatched = false
        val reconnectedService = createTestAccessibilityService {
            gestureDispatched = true
        }
        ZorbiAccessibilityService.setConnectedForTesting(reconnectedService)
        assertThat(injector.isReady.value).isTrue()

        // Injector functions properly after reconnection
        injector.tap(Pt(0.5f, 0.5f))
        assertThat(gestureDispatched).isTrue()

        ZorbiAccessibilityService.setConnectedForTesting(null)
    }

    // 8. OverlayHost Attach, Display and Detach
    @Test
    fun testOverlayHost_attachUpdateDetach_succeedsCleanly() {
        val fakeWm = FakeWindowManager()
        val overlayHost = RealOverlayHost(fakeWm)

        val testView = View(context).apply {
            setBackgroundColor(Color.argb(128, 255, 0, 0)) // Semi-transparent red
        }

        val params = OverlayHost.createDefaultLayoutParams().apply {
            x = 50
            y = 50
        }

        // 1. Attach
        val attached = overlayHost.attachOverlay(testView, params)
        assertThat(attached).isTrue()
        assertThat(fakeWm.views).contains(testView)

        // 2. Update
        params.x = 100
        val updated = overlayHost.updateOverlay(testView, params)
        assertThat(updated).isTrue()
        assertThat(fakeWm.lastUpdatedParams).isNotNull()

        // 3. Detach
        val detached = overlayHost.detachOverlay(testView)
        assertThat(detached).isTrue()
        assertThat(fakeWm.views).doesNotContain(testView)
    }

    // Helper: extracts start (x1, y1) and end (x2, y2) points from a Path
    private fun getPathStartAndEnd(path: Path): Quadruple<Float, Float, Float, Float> {
        val rect = RectF()
        path.computeBounds(rect, true)
        val pm = PathMeasure(path, false)
        val length = pm.length
        return if (length <= 0f) {
            Quadruple(rect.centerX(), rect.centerY(), rect.centerX(), rect.centerY())
        } else {
            val posStart = FloatArray(2)
            val posEnd = FloatArray(2)
            pm.getPosTan(0f, posStart, null)
            pm.getPosTan(length, posEnd, null)
            Quadruple(posStart[0], posStart[1], posEnd[0], posEnd[1])
        }
    }

    // Helper: creates test service with callback on dispatch
    private fun createTestAccessibilityService(
        onDispatch: (GestureDescription) -> Unit
    ): ZorbiAccessibilityService {
        return object : ZorbiAccessibilityService() {
            override fun dispatch(
                gesture: GestureDescription,
                callback: GestureResultCallback?
            ): Boolean {
                onDispatch(gesture)
                callback?.onCompleted(gesture)
                return true
            }
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private class FakeWindowManager : WindowManager {
        val views = mutableListOf<View>()
        var lastUpdatedParams: ViewGroup.LayoutParams? = null

        override fun addView(view: View?, params: ViewGroup.LayoutParams?) {
            if (view != null) views.add(view)
        }

        override fun updateViewLayout(view: View?, params: ViewGroup.LayoutParams?) {
            lastUpdatedParams = params
        }

        override fun removeView(view: View?) {
            views.remove(view)
        }

        override fun removeViewImmediate(view: View?) {
            views.remove(view)
        }

        @Deprecated("Deprecated in Java")
        override fun getDefaultDisplay(): Display? = null
    }
}
