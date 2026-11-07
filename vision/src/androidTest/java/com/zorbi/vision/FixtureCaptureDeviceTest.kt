package com.zorbi.vision

import android.Manifest
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.zorbi.core.PerfMode
import com.zorbi.vision.recorder.LandmarkRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.StringWriter

/**
 * On-device instrumented capture tool for recording real physical-device landmark fixtures (P1-2).
 *
 * Captures real CameraX -> MediaPipe HAND_SINGLE streams into frozen Section 5.3 JSONL format
 * using [LandmarkRecorder].
 *
 * Three required fixtures:
 * 1. neutral_hand.jsonl: Hand held steady in front of front camera (~4-5 seconds).
 * 2. swipe_left.jsonl: Hand swiped smoothly across camera view from right to left.
 * 3. swipe_right.jsonl: Hand swiped smoothly across camera view from left to right.
 */
@RunWith(AndroidJUnit4::class)
class FixtureCaptureDeviceTest {

    companion object {
        private const val TAG = "FixtureCapture"
        private const val WARMUP_TIMEOUT_MS = 40_000L
    }

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val targetPkg = context.packageName
        val testPkg = instrumentation.context.packageName

        instrumentation.uiAutomation.executeShellCommand("pm grant $targetPkg android.permission.CAMERA")
        instrumentation.uiAutomation.executeShellCommand("pm grant $testPkg android.permission.CAMERA")
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/zorbi_fixtures")
    }

    private suspend fun recordFixture(
        fileName: String,
        instructionPrompt: String,
        minRequiredFrames: Int = 5,
        recordDurationMs: Long = 5000L
    ): Int {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        Log.i(TAG, "==================================================================")
        Log.i(TAG, ">>> STARTING FIXTURE CAPTURE: $fileName <<<")
        Log.i(TAG, ">>> INSTRUCTION: $instructionPrompt <<<")
        Log.i(TAG, "==================================================================")

        val tracker = HandSingleTracker(context)
        try {
            tracker.start(PerfMode.QUALITY)

            val memoryWriter = StringWriter()
            val recorder = LandmarkRecorder(defaultTracker = tracker)
            recorder.startRecording(destination = memoryWriter)

            Log.i(TAG, "Waiting for initial hand detection (Timeout: ${WARMUP_TIMEOUT_MS / 1000}s)...")
            Log.i(TAG, ">>> $instructionPrompt <<<")

            val waitStart = SystemClock.elapsedRealtime()
            var handDetected = false
            while (SystemClock.elapsedRealtime() - waitStart < WARMUP_TIMEOUT_MS) {
                if (recorder.recordedFrameCount > 0) {
                    handDetected = true
                    break
                }
                delay(50)
            }

            assertWithMessage(
                "No hand was detected in front of the camera within ${WARMUP_TIMEOUT_MS / 1000}s. " +
                "Please hold your hand clearly visible to the front-facing camera."
            ).that(handDetected).isTrue()

            Log.i(TAG, ">>> HAND DETECTED! RECORDING FOR ${recordDurationMs / 1000}s... <<<")
            Log.i(TAG, ">>> $instructionPrompt <<<")

            val recordStart = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - recordStart < recordDurationMs) {
                delay(100)
            }

            val frameCount = recorder.stopRecording()
            val jsonlContent = memoryWriter.toString()

            Log.i(TAG, "Recording complete. Captured $frameCount frames.")

            // 1. Write to app files directory
            val localFile = File(context.getExternalFilesDir(null) ?: context.filesDir, fileName)
            localFile.writeText(jsonlContent)
            Log.i(TAG, "Wrote local file: ${localFile.absolutePath} (${localFile.length()} bytes)")

            // 2. Copy to /sdcard/Download/zorbi_fixtures/ using shell
            try {
                val shellCmd = "cp ${localFile.absolutePath} /sdcard/Download/zorbi_fixtures/$fileName"
                val pfd = instrumentation.uiAutomation.executeShellCommand(shellCmd)
                pfd.close()
                Log.i(TAG, "Copied backup to /sdcard/Download/zorbi_fixtures/$fileName")
            } catch (e: Exception) {
                Log.w(TAG, "Failed shell copy: ${e.message}")
            }

            assertThat(frameCount).isAtLeast(minRequiredFrames)
            return frameCount
        } finally {
            tracker.stop()
        }
    }

    @Test
    fun captureNeutralHand() {
        runBlocking {
            recordFixture(
                fileName = "neutral_hand.jsonl",
                instructionPrompt = "HOLD ONE HAND FLAT AND STEADY IN FRONT OF THE CAMERA",
                minRequiredFrames = 5,
                recordDurationMs = 5000L
            )
        }
    }

    @Test
    fun captureSwipeLeft() {
        runBlocking {
            recordFixture(
                fileName = "swipe_left.jsonl",
                instructionPrompt = "SWIPE HAND FROM RIGHT TO LEFT ACROSS CAMERA VIEW",
                minRequiredFrames = 5,
                recordDurationMs = 4000L
            )
        }
    }

    @Test
    fun captureSwipeRight() {
        runBlocking {
            recordFixture(
                fileName = "swipe_right.jsonl",
                instructionPrompt = "SWIPE HAND FROM LEFT TO RIGHT ACROSS CAMERA VIEW",
                minRequiredFrames = 5,
                recordDurationMs = 4000L
            )
        }
    }
}
