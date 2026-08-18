package com.example.voice.wakeword

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

interface WakeWordEngine {
    val isRunning: Boolean
    val supportedPhrases: List<String>
    fun startWakeWordDetection(onWakeWordDetected: (String) -> Unit)
    fun stopWakeWordDetection()
}

/**
 * High-performance on-device wake-word detection engine for J.A.R.V.I.S.
 * Operates in both Foreground Service and UI contexts without sending raw audio to cloud APIs.
 */
class JarvisWakeWordEngine(private val context: Context? = null) : WakeWordEngine, RecognitionListener {

    override var isRunning: Boolean = false
        private set

    override val supportedPhrases: List<String> = listOf(
        "JARVIS",
        "HEY JARVIS",
        "OK JARVIS",
        "FRIDAY",
        "EDITH",
        "BHAI JARVIS",
        "SUNO JARVIS",
        "HELLO JARVIS"
    )

    private var onWakeCallback: ((String) -> Unit)? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var restartAttemptCount = 0
    private var isCurrentlyListening = false

    override fun startWakeWordDetection(onWakeWordDetected: (String) -> Unit) {
        this.onWakeCallback = onWakeWordDetected
        this.isRunning = true
        this.restartAttemptCount = 0

        if (context != null && SpeechRecognizer.isRecognitionAvailable(context)) {
            mainHandler.post {
                startRecognizerLoop()
            }
        } else {
            Log.w(TAG, "Speech recognition not available or context null for background spotter.")
        }
    }

    override fun stopWakeWordDetection() {
        this.isRunning = false
        this.onWakeCallback = null
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            destroyRecognizer()
        }
    }

    private fun startRecognizerLoop() {
        if (!isRunning || context == null) return
        try {
            destroyRecognizer()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@JarvisWakeWordEngine)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
            isCurrentlyListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting wake word recognizer: ${e.message}")
            scheduleRestart(1000)
        }
    }

    private fun scheduleRestart(delayMillis: Long) {
        if (!isRunning) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (isRunning) {
                startRecognizerLoop()
            }
        }, delayMillis)
    }

    private fun destroyRecognizer() {
        try {
            isCurrentlyListening = false
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore teardown errors
        } finally {
            speechRecognizer = null
        }
    }

    fun checkAndTriggerWakeWord(phrase: String): Boolean {
        if (!isRunning) return false
        val normalized = phrase.uppercase().trim()
        val matched = supportedPhrases.any { keyword ->
            normalized.contains(keyword) || normalized.contains(keyword.replace(" ", ""))
        }
        if (matched) {
            Log.i(TAG, "Wake word detected: $phrase")
            onWakeCallback?.invoke(phrase)
            return true
        }
        return false
    }

    // Speech Recognition Listener implementation
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {
        isCurrentlyListening = false
        if (!isRunning) return

        // Exponential backoff for repeated errors to conserve CPU/Battery
        val delay = when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 500L
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 150L
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 2000L
            else -> 800L
        }
        scheduleRestart(delay)
    }

    override fun onResults(results: Bundle?) {
        isCurrentlyListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val spoken = matches?.firstOrNull()?.trim() ?: ""
        if (spoken.isNotBlank()) {
            if (checkAndTriggerWakeWord(spoken)) {
                // If wake word was detected, callback handles transition.
                return
            }
        }
        // Restart wake-word loop immediately
        scheduleRestart(100L)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val partial = matches?.firstOrNull()?.trim() ?: ""
        if (partial.isNotBlank()) {
            if (checkAndTriggerWakeWord(partial)) {
                destroyRecognizer()
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        private const val TAG = "JarvisWakeWordEngine"

        /**
         * Normalizes raw spoken input by stripping any wake word prefix.
         * E.g. "Hey Jarvis open WhatsApp" -> "open WhatsApp"
         */
        fun stripWakeWordPrefix(input: String): String {
            var text = input.trim()
            val prefixes = listOf(
                "hey jarvis", "ok jarvis", "okay jarvis", "jarvis",
                "friday", "edith", "bhai jarvis", "suno jarvis", "hello jarvis",
                "hey friday", "ok friday", "hey edith"
            )

            for (prefix in prefixes) {
                if (text.startsWith(prefix, ignoreCase = true)) {
                    text = text.substring(prefix.length).trim()
                    // Strip optional comma or punctuation
                    text = text.removePrefix(",").removePrefix(":").removePrefix("-").trim()
                    break
                }
            }
            return text
        }
    }
}
