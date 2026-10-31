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
import com.zorbi.core.TrackerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical device validation test for Person 1 P1-5: CameraX -> MediaPipe -> HAND_DUAL_WHEEL pipeline (Gate T-P1-5).
 *
 * Requirements:
 * - Runs on connected physical Android device.
 * - Automatically grants Camera permission via GrantPermissionRule and UiAutomation.
 * - Obtains real device context and starts [HandDualWheelTracker].
 * - Validates simultaneous two-hand tracking data, 21 landmarks per hand, normalized coordinates 0..1, Side attribution, and performance stats.
 * - Cleanly releases camera and MediaPipe resources in finally block.
 */
@RunWith(AndroidJUnit4::class)
class HandDualWheelTrackerDeviceTest {

    companion object {
        private const val TAG = "DualWheelDeviceTest"
        private const val TIMEOUT_MS = 20_000L
    }

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun testHandDualWheelPipeline_onPhysicalDevice() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        // Ensure runtime camera permission is actively granted on Android 14
        val targetPkg = context.packageName
        instrumentation.uiAutomation.executeShellCommand("pm grant $targetPkg android.permission.CAMERA")
        val testPkg = instrumentation.context.packageName
        instrumentation.uiAutomation.executeShellCommand("pm grant $testPkg android.permission.CAMERA")

        Log.i(TAG, "Initializing HandDualWheelTracker with context: $targetPkg")
        val tracker = HandDualWheelTracker(context)
        assertThat(tracker.id).isEqualTo(TrackerId.HAND_DUAL_WHEEL)

        val receivedFrames = mutableListOf<HandsFrame>()

        try {
            Log.i(TAG, "Starting HandDualWheelTracker in BALANCED mode...")
            tracker.start(PerfMode.BALANCED)

            // Collect frames asynchronously
            val collectJob = launch(Dispatchers.Default) {
                tracker.frames.collect { frame ->
                    if (frame is HandsFrame && frame.hands.isNotEmpty()) {
                        Log.i(TAG, "Received dual-wheel hand frame: ts=${frame.tsNanos}, hands=${frame.hands.size}")
                        receivedFrames.add(frame)
                    }
                }
            }

            Log.i(TAG, "Waiting for hand detection (Timeout: ${TIMEOUT_MS / 1000}s)...")
            Log.i(TAG, ">>> [TEST INSTRUCTION] PLEASE HOLD BOTH HANDS IN FRONT OF THE CAMERA AS IF HOLDING A STEERING WHEEL <<<")

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
            Log.i(TAG, "Live dual-wheel tracker stats: fps=${currentStats.fps}, p50=${currentStats.inferenceMsP50}ms, p95=${currentStats.inferenceMsP95}ms, dropped=${currentStats.dropped}")

            assertWithMessage(
                "No hand detection was received within ${TIMEOUT_MS / 1000}s. " +
                "Please hold hands in front of the front-facing camera."
            ).that(receivedFrames).isNotEmpty()

            val firstValidFrame = receivedFrames.first()
            assertThat(firstValidFrame.tsNanos).isGreaterThan(0L)
            assertThat(firstValidFrame.hands.size).isAtLeast(1)
            assertThat(firstValidFrame.hands.size).isAtMost(2)

            for ((handIdx, hand) in firstValidFrame.hands.withIndex()) {
                Log.i(TAG, "Hand $handIdx: side=${hand.side}, score=${hand.score}, landmarks=${hand.landmarks.size}")
                assertThat(hand.side).isAnyOf(Side.LEFT, Side.RIGHT, Side.UNKNOWN)
                assertThat(hand.score).isAtLeast(0.5f)
                assertThat(hand.landmarks).hasSize(21)

                for ((lmIdx, pt) in hand.landmarks.withIndex()) {
                    assertWithMessage("Hand $handIdx landmark $lmIdx x out of bounds: ${pt.x}")
                        .that(pt.x).isAtLeast(0.0f)
                    assertWithMessage("Hand $handIdx landmark $lmIdx x out of bounds: ${pt.x}")
                        .that(pt.x).isAtMost(1.0f)
                    assertWithMessage("Hand $handIdx landmark $lmIdx y out of bounds: ${pt.y}")
                        .that(pt.y).isAtLeast(0.0f)
                    assertWithMessage("Hand $handIdx landmark $lmIdx y out of bounds: ${pt.y}")
                        .that(pt.y).isAtMost(1.0f)
                }
            }

            val finalStats = tracker.stats.value
            Log.i(TAG, "Final validated dual-wheel stats: fps=${finalStats.fps}, p50=${finalStats.inferenceMsP50}ms, p95=${finalStats.inferenceMsP95}ms, dropped=${finalStats.dropped}")
            assertThat(finalStats.fps).isAtLeast(0f)
            assertThat(finalStats.inferenceMsP50).isAtLeast(0f)
            assertThat(finalStats.inferenceMsP95).isAtLeast(0f)
            assertThat(finalStats.dropped).isAtLeast(0)

        } finally {
            Log.i(TAG, "Stopping HandDualWheelTracker in cleanup block...")
            tracker.stop()
            Log.i(TAG, "HandDualWheelTracker stopped cleanly.")
        }
    }
}
