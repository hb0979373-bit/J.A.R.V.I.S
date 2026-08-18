package com.example.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "jarvis_preferences")

enum class ProactiveMode {
    OFF,
    IMPORTANT_ONLY,
    BALANCED,
    ACTIVE
}

enum class ResponseMode {
    SHORT,
    NORMAL,
    DETAILED,
    VOICE_FRIENDLY
}

data class JarvisSettings(
    val assistantName: String = "JARVIS",
    val proactiveMode: ProactiveMode = ProactiveMode.BALANCED,
    val proactiveCooldownMinutes: Int = 10,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00",
    val focusModeEnabled: Boolean = false,
    val studyModeEnabled: Boolean = false,
    val responseMode: ResponseMode = ResponseMode.VOICE_FRIENDLY,
    val speechSpeed: Float = 1.0f,
    val speechPitch: Float = 0.95f,
    val speechLanguage: String = "en-US",
    val aiProviderName: String = "Google Gemini",
    val continuousListeningEnabled: Boolean = false
)

class JarvisPreferencesRepository(private val context: Context) {

    private object Keys {
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val PROACTIVE_MODE = stringPreferencesKey("proactive_mode")
        val PROACTIVE_COOLDOWN = intPreferencesKey("proactive_cooldown")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_HOURS_START = stringPreferencesKey("quiet_hours_start")
        val QUIET_HOURS_END = stringPreferencesKey("quiet_hours_end")
        val FOCUS_MODE = booleanPreferencesKey("focus_mode")
        val STUDY_MODE = booleanPreferencesKey("study_mode")
        val RESPONSE_MODE = stringPreferencesKey("response_mode")
        val SPEECH_SPEED = floatPreferencesKey("speech_speed")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val SPEECH_LANGUAGE = stringPreferencesKey("speech_language")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val CONTINUOUS_LISTENING = booleanPreferencesKey("continuous_listening")
    }

    val settingsFlow: Flow<JarvisSettings> = context.dataStore.data.map { prefs ->
        JarvisSettings(
            assistantName = prefs[Keys.ASSISTANT_NAME] ?: "JARVIS",
            proactiveMode = prefs[Keys.PROACTIVE_MODE]?.let { runCatching { ProactiveMode.valueOf(it) }.getOrNull() } ?: ProactiveMode.BALANCED,
            proactiveCooldownMinutes = prefs[Keys.PROACTIVE_COOLDOWN] ?: 10,
            quietHoursEnabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: true,
            quietHoursStart = prefs[Keys.QUIET_HOURS_START] ?: "22:00",
            quietHoursEnd = prefs[Keys.QUIET_HOURS_END] ?: "07:00",
            focusModeEnabled = prefs[Keys.FOCUS_MODE] ?: false,
            studyModeEnabled = prefs[Keys.STUDY_MODE] ?: false,
            responseMode = prefs[Keys.RESPONSE_MODE]?.let { runCatching { ResponseMode.valueOf(it) }.getOrNull() } ?: ResponseMode.VOICE_FRIENDLY,
            speechSpeed = prefs[Keys.SPEECH_SPEED] ?: 1.0f,
            speechPitch = prefs[Keys.SPEECH_PITCH] ?: 0.95f,
            speechLanguage = prefs[Keys.SPEECH_LANGUAGE] ?: "en-US",
            aiProviderName = prefs[Keys.AI_PROVIDER] ?: "Google Gemini",
            continuousListeningEnabled = prefs[Keys.CONTINUOUS_LISTENING] ?: false
        )
    }

    suspend fun updateAssistantName(name: String) {
        context.dataStore.edit { it[Keys.ASSISTANT_NAME] = name }
    }

    suspend fun updateProactiveMode(mode: ProactiveMode) {
        context.dataStore.edit { it[Keys.PROACTIVE_MODE] = mode.name }
    }

    suspend fun updateProactiveCooldown(minutes: Int) {
        context.dataStore.edit { it[Keys.PROACTIVE_COOLDOWN] = minutes }
    }

    suspend fun updateQuietHours(enabled: Boolean, start: String, end: String) {
        context.dataStore.edit {
            it[Keys.QUIET_HOURS_ENABLED] = enabled
            it[Keys.QUIET_HOURS_START] = start
            it[Keys.QUIET_HOURS_END] = end
        }
    }

    suspend fun setFocusMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FOCUS_MODE] = enabled }
    }

    suspend fun setStudyMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STUDY_MODE] = enabled }
    }

    suspend fun updateResponseMode(mode: ResponseMode) {
        context.dataStore.edit { it[Keys.RESPONSE_MODE] = mode.name }
    }

    suspend fun updateSpeechSettings(speed: Float, pitch: Float, language: String) {
        context.dataStore.edit {
            it[Keys.SPEECH_SPEED] = speed
            it[Keys.SPEECH_PITCH] = pitch
            it[Keys.SPEECH_LANGUAGE] = language
        }
    }

    suspend fun setContinuousListening(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CONTINUOUS_LISTENING] = enabled }
    }
}
