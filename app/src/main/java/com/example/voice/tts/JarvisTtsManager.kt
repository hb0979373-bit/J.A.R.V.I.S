package com.example.voice.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class JarvisTtsManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechAmplitude = MutableStateFlow(0f)
    val speechAmplitude: StateFlow<Float> = _speechAmplitude.asStateFlow()

    private var currentSpeechRate: Float = 1.0f
    private var currentSpeechPitch: Float = 0.94f // Slightly deeper male tone
    private var currentLanguage: Locale = Locale.US

    private var onSpeechDoneCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupDefaultVoice()
            setupUtteranceListener()
            onInitComplete(true)
        } else {
            isInitialized = false
            onInitComplete(false)
        }
    }

    private fun setupDefaultVoice() {
        val ttsInstance = tts ?: return
        try {
            ttsInstance.language = currentLanguage
            ttsInstance.setPitch(currentSpeechPitch)
            ttsInstance.setSpeechRate(currentSpeechRate)

            // Select best available male voice
            val voices = ttsInstance.voices
            if (!voices.isNullOrEmpty()) {
                val maleVoice = voices.find { voice ->
                    val nameLower = voice.name.lowercase()
                    !voice.isNetworkConnectionRequired &&
                            (nameLower.contains("male") || nameLower.contains("en-us-x-sfg") || nameLower.contains("en-gb-x-rjs") || nameLower.contains("male-compact")) &&
                            !nameLower.contains("female")
                } ?: voices.find { voice ->
                    val nameLower = voice.name.lowercase()
                    (nameLower.contains("male") || nameLower.contains("man")) && !nameLower.contains("female")
                } ?: voices.firstOrNull { voice ->
                    voice.locale.language == currentLanguage.language
                }

                if (maleVoice != null) {
                    ttsInstance.voice = maleVoice
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _speechAmplitude.value = 0.75f
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                _speechAmplitude.value = 0f
                onSpeechDoneCallback?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _speechAmplitude.value = 0f
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                _speechAmplitude.value = 0f
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                // Vary visual amplitude dynamically during natural word cadences
                val variation = 0.4f + (Math.random().toFloat() * 0.6f)
                _speechAmplitude.value = variation
            }
        })
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank()) {
            onComplete?.invoke()
            return
        }
        this.onSpeechDoneCallback = onComplete

        val cleanText = sanitizeForSpeech(text)
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSpeaking.value = false
            _speechAmplitude.value = 0f
        }
    }

    fun updateSettings(speed: Float, pitch: Float, languageTag: String) {
        this.currentSpeechRate = speed.coerceIn(0.5f, 2.0f)
        this.currentSpeechPitch = pitch.coerceIn(0.5f, 1.5f)

        this.currentLanguage = when {
            languageTag.startsWith("hi", ignoreCase = true) -> Locale("hi", "IN")
            languageTag.contains("GB", ignoreCase = true) -> Locale.UK
            else -> Locale.US
        }

        if (isInitialized) {
            tts?.apply {
                language = currentLanguage
                setSpeechRate(currentSpeechRate)
                setPitch(currentSpeechPitch)
                setupDefaultVoice()
            }
        }
    }

    fun getAvailableVoices(): List<Voice> {
        return try {
            tts?.voices?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setSpecificVoice(voice: Voice) {
        if (isInitialized) {
            tts?.voice = voice
        }
    }

    private fun sanitizeForSpeech(text: String): String {
        // Strip markdown code blocks, asterisks, hashtags, urls for cleaner natural voice
        return text
            .replace(Regex("```[\\s\\S]*?```"), "Code block omitted.")
            .replace(Regex("`.*?`"), "")
            .replace(Regex("[*#_~]"), "")
            .replace(Regex("https?://\\S+"), "link")
            .replace(Regex("\n+"), ". ")
            .trim()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isInitialized = false
    }
}
