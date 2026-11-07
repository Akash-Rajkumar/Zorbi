package com.zorbi.ui.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Global Voice Coach engine providing audible speech guidance across ZORBI.
 * Automatically identifies games and announces supported gesture actions to guide the user.
 */
object ZorbiVoiceCoach {
    private const val TAG = "ZorbiVoiceCoach"

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingMessages = mutableListOf<String>()

    fun init(context: Context) {
        if (tts != null) return
        mainHandler.post {
            try {
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = Locale.US
                        isInitialized = true
                        Log.i(TAG, "TextToSpeech Voice Coach successfully initialized")
                        Log.i("ZORBI_STARTUP", "Voice coach initialized successfully (TTS ready)")
                        // Flush any queued messages
                        synchronized(pendingMessages) {
                            for (msg in pendingMessages) {
                                tts?.speak(msg, TextToSpeech.QUEUE_ADD, null, "ZorbiCoachQueue")
                            }
                            pendingMessages.clear()
                        }
                    } else {
                        Log.w(TAG, "TextToSpeech initialization returned status: $status")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize TextToSpeech", e)
            }
        }
    }

    /**
     * Speaks the specified text guidance through the phone's speaker.
     */
    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) return
        mainHandler.post {
            try {
                if (isInitialized && tts != null) {
                    val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    tts?.speak(text, queueMode, null, "ZorbiVoiceCoach_${System.currentTimeMillis()}")
                } else {
                    synchronized(pendingMessages) {
                        if (flush) pendingMessages.clear()
                        pendingMessages.add(text)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error speaking voice text: $text", e)
            }
        }
    }

    /**
     * Immediately stops any active voice speech playback.
     */
    fun stop() {
        mainHandler.post {
            try {
                tts?.stop()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Safely releases TTS engine resources upon shutdown.
     */
    fun shutdown() {
        mainHandler.post {
            try {
                tts?.stop()
                tts?.shutdown()
                tts = null
                isInitialized = false
            } catch (_: Throwable) {}
        }
    }
}
