package com.example.voice.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class SpeechState {
    data object Idle : SpeechState()
    data object ReadyToListen : SpeechState()
    data object Listening : SpeechState()
    data class Processing(val partialText: String = "") : SpeechState()
    data class Success(val spokenText: String) : SpeechState()
    data class Error(val message: String, val errorCode: Int = 0) : SpeechState()
}

class JarvisSpeechRecognizer(
    private val context: Context,
    private val onResult: (String) -> Unit = {},
    private val onError: (String) -> Unit = {}
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _audioRmsDb = MutableStateFlow(0f)
    val audioRmsDb: StateFlow<Float> = _audioRmsDb.asStateFlow()

    private var currentLanguageTag: String = "en-US"
    private var isListening = false

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(languageTag: String = "en-US") {
        if (!isAvailable()) {
            _speechState.value = SpeechState.Error("Speech recognition is not available on this device.")
            onError("Speech recognition not available")
            return
        }

        currentLanguageTag = languageTag
        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@JarvisSpeechRecognizer)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            _speechState.value = SpeechState.ReadyToListen
        } catch (e: Exception) {
            _speechState.value = SpeechState.Error(e.localizedMessage ?: "Failed to start listening")
            onError(e.localizedMessage ?: "Recognition error")
        }
    }

    fun stopListening() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            speechRecognizer = null
            _audioRmsDb.value = 0f
            if (_speechState.value is SpeechState.Listening || _speechState.value is SpeechState.ReadyToListen) {
                _speechState.value = SpeechState.Idle
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        _speechState.value = SpeechState.Listening
    }

    override fun onBeginningOfSpeech() {
        _speechState.value = SpeechState.Listening
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Map dB (typically -2 to 10) to a normalized 0..1 range for orb pulsing
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        _audioRmsDb.value = normalized
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _audioRmsDb.value = 0f
        _speechState.value = SpeechState.Processing()
    }

    override fun onError(error: Int) {
        _audioRmsDb.value = 0f
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client speech error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network connection required for speech"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            else -> "Speech recognition error ($error)"
        }

        _speechState.value = SpeechState.Error(errorMessage, error)
        onError(errorMessage)
    }

    override fun onResults(results: Bundle?) {
        _audioRmsDb.value = 0f
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val spokenText = matches?.firstOrNull()?.trim() ?: ""

        if (spokenText.isNotBlank()) {
            _speechState.value = SpeechState.Success(spokenText)
            onResult(spokenText)
        } else {
            _speechState.value = SpeechState.Error("No speech recognized")
            onError("No speech recognized")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val partial = matches?.firstOrNull()?.trim() ?: ""
        if (partial.isNotBlank()) {
            _speechState.value = SpeechState.Processing(partial)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        stopListening()
    }
}
