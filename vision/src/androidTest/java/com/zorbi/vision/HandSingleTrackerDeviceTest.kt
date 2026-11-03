package com.zorbi.vision

import android.Manifest
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.zorbi.core.HandsFrame
import com.zorbi.core.PerfMode
import com.zorbi.core.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical device validation test for Person 1 P1-1: CameraX -> MediaPipe -> HAND_SINGLE pipeline.
 *
 * Requirements:
 * - Runs directly on connected physical Android device.
 * - Automatically grants Camera permission via GrantPermissionRule and UiAutomation.
 * - Obtains real device context and starts [HandSingleTracker].
 * - Validates 21 landmarks, normalized coordinates 0..1, valid Side enum, and performance stats.
 * - Releases camera and MediaPipe resources cleanly in finally block.
 */
@RunWith(AndroidJUnit4::class)
class HandSingleTrackerDeviceTest {

    companion object {
        private const val TAG = "HandSingleDeviceTest"
        private const val TIMEOUT_MS = 20_000L
    }

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun testHandSinglePipeline_onPhysicalDevice() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        // Ensure runtime camera permission is actively granted on Android 14
        val targetPkg = context.packageName
        instrumentation.uiAutomation.executeShellCommand("pm grant $targetPkg android.permission.CAMERA")
        val testPkg = instrumentation.context.packageName
        instrumentation.uiAutomation.executeShellCommand("pm grant $testPkg android.permission.CAMERA")

        Log.i(TAG, "Initializing HandSingleTracker with target context: $targetPkg")
        val tracker = HandSingleTracker(context)
        val receivedFrames = mutableListOf<HandsFrame>()

        try {
            Log.i(TAG, "Starting HandSingleTracker in QUALITY mode...")
            tracker.start(PerfMode.QUALITY)

            // Collect frames asynchronously
            val collectJob = launch(Dispatchers.Default) {
                tracker.frames.collect { frame ->
                    if (frame is HandsFrame && frame.hands.isNotEmpty()) {
                        Log.i(TAG, "Received hand frame: ts=${frame.tsNanos}, hands=${frame.hands.size}")
                        receivedFrames.add(frame)
                    }
                }
            }

            Log.i(TAG, "Waiting for hand detection (Timeout: ${TIMEOUT_MS / 1000}s)...")
            Log.i(TAG, ">>> [TEST INSTRUCTION] PLEASE HOLD ONE HAND IN FRONT OF THE FRONT CAMERA <<<")

            val startTime = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - startTime < TIMEOUT_MS) {
                if (receivedFrames.isNotEmpty()) {
                    Log.i(TAG, "Received ${receivedFrames.size} hand detection frames.")
                    break
                }
                delay(200)
            }

            collectJob.cancelAndJoin()

            // Inspect live tracker performance metrics
            val currentStats = tracker.stats.value
            Log.i(TAG, "Live tracker stats after run: fps=${currentStats.fps}, p50=${currentStats.inferenceMsP50}ms, p95=${currentStats.inferenceMsP95}ms, dropped=${currentStats.dropped}")

            // 1. Verify a hand was detected within timeout window
            assertWithMessage(
                "No hand detection was received within ${TIMEOUT_MS / 1000}s. " +
                "Please ensure the front camera is unobstructed and hold one hand clearly visible in front of the front camera."
            ).that(receivedFrames).isNotEmpty()

            val firstValidFrame = receivedFrames.first()
            assertThat(firstValidFrame.tsNanos).isGreaterThan(0L)
            assertThat(firstValidFrame.hands).hasSize(1)

            val hand = firstValidFrame.hands.first()
            Log.i(TAG, "Hand details: side=${hand.side}, score=${hand.score}, landmarks=${hand.landmarks.size}")

            // 2. Validate handedness side
            assertThat(hand.side).isAnyOf(Side.LEFT, Side.RIGHT, Side.UNKNOWN)
            assertThat(hand.score).isAtLeast(0.5f)

            // 3. Validate exactly 21 landmarks
            assertThat(hand.landmarks).hasSize(21)

            // 4. Validate all landmarks are within normalized 0..1 bounds
            for ((index, pt) in hand.landmarks.withIndex()) {
                assertWithMessage("Landmark $index x is out of bounds: ${pt.x}")
                    .that(pt.x).isAtLeast(0.0f)
                assertWithMessage("Landmark $index x is out of bounds: ${pt.x}")
                    .that(pt.x).isAtMost(1.0f)
                assertWithMessage("Landmark $index y is out of bounds: ${pt.y}")
                    .that(pt.y).isAtLeast(0.0f)
                assertWithMessage("Landmark $index y is out of bounds: ${pt.y}")
                    .that(pt.y).isAtMost(1.0f)
            }

            // 5. Inspect and validate tracker performance stats
            val finalStats = tracker.stats.value
            Log.i(TAG, "Final validated stats: fps=${finalStats.fps}, p50=${finalStats.inferenceMsP50}ms, p95=${finalStats.inferenceMsP95}ms, dropped=${finalStats.dropped}")
            assertThat(finalStats.fps).isAtLeast(0f)
            assertThat(finalStats.inferenceMsP50).isAtLeast(0f)
            assertThat(finalStats.inferenceMsP95).isAtLeast(0f)
            assertThat(finalStats.dropped).isAtLeast(0)

        } finally {
            Log.i(TAG, "Stopping HandSingleTracker in cleanup block...")
            tracker.stop()
            Log.i(TAG, "HandSingleTracker stopped cleanly.")
        }
    }
}

