package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ChatMessage
import com.example.ai.ConnectionTestResult
import com.example.ai.GeminiAiProvider
import com.example.commands.CommandRouter
import com.example.commands.RouteResult
import com.example.core.datastore.JarvisPreferencesRepository
import com.example.core.datastore.JarvisSettings
import com.example.core.datastore.ProactiveMode
import com.example.core.datastore.ResponseMode
import com.example.core.security.KeystoreManager
import com.example.data.local.db.JarvisDatabase
import com.example.data.local.db.entity.ActionHistoryItem
import com.example.data.local.db.entity.JarvisReminder
import com.example.data.local.db.entity.JarvisTask
import com.example.data.local.db.entity.MemoryItem
import com.example.data.repository.JarvisRepository
import com.example.device.CommunicationManager
import com.example.device.CommunicationType
import com.example.device.DeviceActionExecutor
import com.example.device.PendingCommunicationAction
import com.example.device.access.SpecialAccessItem
import com.example.device.access.SpecialAccessManager
import com.example.proactive.ProactiveEngine
import com.example.services.JarvisAccessibilityService
import com.example.services.JarvisNotificationListenerService
import com.example.ui.components.OrbState
import com.example.voice.speech.JarvisSpeechRecognizer
import com.example.voice.speech.SpeechState
import com.example.voice.tts.JarvisTtsManager
import com.example.voice.wakeword.JarvisWakeWordEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val keystoreManager = KeystoreManager(application)
    private val preferencesRepository = JarvisPreferencesRepository(application)
    private val database = JarvisDatabase.getInstance(application)
    private val repository = JarvisRepository(database)
    private val deviceExecutor = DeviceActionExecutor(application)
    private val communicationManager = CommunicationManager(application)
    private val aiProvider = GeminiAiProvider(keystoreManager)

    private val commandRouter = CommandRouter(
        deviceExecutor = deviceExecutor,
        communicationManager = communicationManager,
        repository = repository,
        preferencesRepository = preferencesRepository,
        aiProvider = aiProvider
    )

    private val ttsManager = JarvisTtsManager(application) { isSuccess ->
        if (isSuccess) {
            applyCurrentSpeechSettings()
        }
    }

    private val speechRecognizer = JarvisSpeechRecognizer(
        context = application,
        onResult = { spokenText -> handleSpeechRecognized(spokenText) },
        onError = { errorMsg -> handleSpeechError(errorMsg) }
    )

    private val wakeWordEngine = JarvisWakeWordEngine()

    private val proactiveEngine = ProactiveEngine(
        preferencesRepository = preferencesRepository,
        repository = repository,
        deviceExecutor = deviceExecutor,
        onSpeakAlert = { alertText ->
            speakProactiveAlert(alertText)
        }
    )

    // UI States
    private val _orbState = MutableStateFlow(OrbState.STANDBY)
    val orbState: StateFlow<OrbState> = _orbState.asStateFlow()

    private val _userSpokenText = MutableStateFlow("")
    val userSpokenText: StateFlow<String> = _userSpokenText.asStateFlow()

    private val _assistantResponseText = MutableStateFlow("")
    val assistantResponseText: StateFlow<String> = _assistantResponseText.asStateFlow()

    private val _statusNotice = MutableStateFlow("Tap orb or speak to initiate")
    val statusNotice: StateFlow<String> = _statusNotice.asStateFlow()

    private val _batteryPercentage = MutableStateFlow(deviceExecutor.getBatteryPercentage())
    val batteryPercentage: StateFlow<Int> = _batteryPercentage.asStateFlow()

    private val _pendingConfirmationAction = MutableStateFlow<PendingCommunicationAction?>(null)
    val pendingConfirmationAction: StateFlow<PendingCommunicationAction?> = _pendingConfirmationAction.asStateFlow()

    private val _connectionStatus = MutableStateFlow<ConnectionTestResult>(
        if (keystoreManager.hasApiKey()) ConnectionTestResult.Success("Gemini Core Connected")
        else ConnectionTestResult.NotConfigured
    )
    val connectionStatus: StateFlow<ConnectionTestResult> = _connectionStatus.asStateFlow()

    private val _isApiKeyConfigured = MutableStateFlow<Boolean>(keystoreManager.hasApiKey())
    val isApiKeyConfigured: StateFlow<Boolean> = _isApiKeyConfigured.asStateFlow()

    private val _currentMaskedApiKey = MutableStateFlow<String>(keystoreManager.getMaskedApiKey())
    val currentMaskedApiKey: StateFlow<String> = _currentMaskedApiKey.asStateFlow()

    val settings: StateFlow<JarvisSettings> = preferencesRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JarvisSettings()
    )

    val memories: StateFlow<List<MemoryItem>> = repository.allMemories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val tasks: StateFlow<List<JarvisTask>> = repository.allTasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val reminders: StateFlow<List<JarvisReminder>> = repository.allReminders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val actionHistory: StateFlow<List<ActionHistoryItem>> = repository.recentHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val specialAccessManager = SpecialAccessManager(application)
    private val _specialAccessList = MutableStateFlow<List<SpecialAccessItem>>(emptyList())
    val specialAccessList: StateFlow<List<SpecialAccessItem>> = _specialAccessList.asStateFlow()

    val audioAmplitude: StateFlow<Float> = speechRecognizer.audioRmsDb
    private val conversationHistory = mutableListOf<ChatMessage>()
    private var proactiveLoopJob: Job? = null

    init {
        observeSpeechState()
        startProactiveMonitoring()
        refreshSpecialAccesses()
    }

    private fun observeSpeechState() {
        viewModelScope.launch {
            speechRecognizer.speechState.collect { state ->
                when (state) {
                    is SpeechState.Idle -> {
                        if (_orbState.value == OrbState.LISTENING) {
                            _orbState.value = OrbState.STANDBY
                            _statusNotice.value = "Standing by"
                        }
                    }
                    is SpeechState.ReadyToListen -> {
                        _orbState.value = OrbState.LISTENING
                        _statusNotice.value = "Listening..."
                    }
                    is SpeechState.Listening -> {
                        _orbState.value = OrbState.LISTENING
                        _statusNotice.value = "Listening..."
                    }
                    is SpeechState.Processing -> {
                        _orbState.value = OrbState.THINKING
                        _statusNotice.value = "Analyzing audio stream..."
                    }
                    is SpeechState.Error -> {
                        _orbState.value = OrbState.ERROR
                        _statusNotice.value = state.message
                        delay(2000)
                        _orbState.value = OrbState.STANDBY
                        _statusNotice.value = "Standing by"
                    }
                    is SpeechState.Success -> {
                        // Handled in onResult
                    }
                }
            }
        }
    }

    private fun startProactiveMonitoring() {
        proactiveLoopJob?.cancel()
        proactiveLoopJob = viewModelScope.launch {
            while (true) {
                delay(30000)
                _batteryPercentage.value = deviceExecutor.getBatteryPercentage()
                proactiveEngine.evaluateProactiveTriggers(settings.value)
            }
        }
    }

    fun onOrbClicked() {
        if (ttsManager.isSpeaking.value) {
            // Immediate user interruption
            ttsManager.stop()
            _orbState.value = OrbState.STANDBY
            _statusNotice.value = "Interrupted"
            return
        }

        if (_orbState.value == OrbState.LISTENING) {
            speechRecognizer.stopListening()
            _orbState.value = OrbState.STANDBY
            _statusNotice.value = "Standing by"
            return
        }

        startListening()
    }

    fun startListening() {
        ttsManager.stop()
        _userSpokenText.value = ""
        _assistantResponseText.value = ""
        _statusNotice.value = "Listening..."
        _orbState.value = OrbState.LISTENING
        speechRecognizer.startListening(settings.value.speechLanguage)
    }

    fun stopListening() {
        speechRecognizer.stopListening()
        _orbState.value = OrbState.STANDBY
    }

    private fun handleSpeechRecognized(spokenText: String) {
        _userSpokenText.value = spokenText
        _orbState.value = OrbState.THINKING
        _statusNotice.value = "Processing command..."

        viewModelScope.launch {
            conversationHistory.add(ChatMessage("user", spokenText))
            val result = commandRouter.processCommand(spokenText, conversationHistory.takeLast(6))

            when (result) {
                is RouteResult.SpokenResponse -> {
                    respondWithSpeech(result.text)
                }
                is RouteResult.AiGenerated -> {
                    conversationHistory.add(ChatMessage("model", result.text))
                    respondWithSpeech(result.text)
                }
                is RouteResult.ActionExecuted -> {
                    respondWithSpeech(result.message)
                }
                is RouteResult.ConfirmationRequired -> {
                    _pendingConfirmationAction.value = result.action
                    respondWithSpeech(result.promptText)
                }
                is RouteResult.Error -> {
                    _orbState.value = OrbState.ERROR
                    _assistantResponseText.value = result.message
                    _statusNotice.value = "Command encountered an issue"
                    ttsManager.speak(result.message) {
                        _orbState.value = OrbState.STANDBY
                        _statusNotice.value = "Standing by"
                    }
                }
            }
        }
    }

    private fun handleSpeechError(errorMsg: String) {
        _orbState.value = OrbState.ERROR
        _statusNotice.value = errorMsg
        viewModelScope.launch {
            delay(2200)
            _orbState.value = OrbState.STANDBY
            _statusNotice.value = "Standing by"
        }
    }

    private fun respondWithSpeech(text: String) {
        _assistantResponseText.value = text
        _orbState.value = OrbState.SPEAKING
        _statusNotice.value = "Responding"

        ttsManager.speak(text) {
            _orbState.value = OrbState.STANDBY
            _statusNotice.value = "Standing by"
            // Follow-up interaction support
            if (settings.value.continuousListeningEnabled) {
                viewModelScope.launch {
                    delay(500)
                    startListening()
                }
            }
        }
    }

    // Safety & Confirmation Actions
    fun confirmPendingAction() {
        val action = _pendingConfirmationAction.value ?: return
        _pendingConfirmationAction.value = null

        viewModelScope.launch {
            when (action.type) {
                CommunicationType.PHONE_CALL -> {
                    val success = communicationManager.executePhoneCall(action.targetNumber)
                    val msg = if (success) "Initiating call to ${action.targetName}." else "Unable to start phone dialer."
                    repository.logAction("Call ${action.targetName}", "COMMUNICATION_CALL", msg, success)
                    respondWithSpeech(msg)
                }
                CommunicationType.SEND_SMS -> {
                    val success = communicationManager.executeSendSms(action.targetNumber, action.messageBody)
                    val msg = if (success) "Opening messaging interface for ${action.targetName}." else "Unable to open messaging."
                    repository.logAction("SMS to ${action.targetName}", "COMMUNICATION_SMS", msg, success)
                    respondWithSpeech(msg)
                }
                CommunicationType.DELETE_DATA -> {
                    respondWithSpeech("Requested data clearance executed, Sir.")
                }
                CommunicationType.SYSTEM_SETTING -> {
                    respondWithSpeech("System configuration applied.")
                }
            }
        }
    }

    fun cancelPendingAction() {
        _pendingConfirmationAction.value = null
        respondWithSpeech("Operation aborted, Sir.")
    }

    // Screen Assistant
    fun analyzeCurrentScreen() {
        if (!JarvisAccessibilityService.isRunning) {
            respondWithSpeech("Accessibility service is not active. Please grant accessibility permission in Settings > Special Access.")
            return
        }
        val screenInfo = JarvisAccessibilityService.getScreenContext()
        if (screenInfo != null && screenInfo.visibleTexts.isNotEmpty()) {
            val summary = "Screen analysis for ${screenInfo.activePackage}: ${screenInfo.visibleTexts.take(4).joinToString(", ")}."
            respondWithSpeech(summary)
        } else {
            respondWithSpeech("Screen accessibility link active, but no readable text detected on current window.")
        }
    }

    // Notification Assistant
    fun readRecentNotifications() {
        if (!JarvisNotificationListenerService.isConnected) {
            respondWithSpeech("Notification Listener is not enabled. Please enable it in Settings > Special Access > Notification Access.")
            return
        }
        val notifs = JarvisNotificationListenerService.getRecentNotifications()
        if (notifs.isNotEmpty()) {
            val text = "You have ${notifs.size} recent notifications: " + notifs.take(3).joinToString("; ") { "${it.appLabel}: ${it.title} - ${it.text}" }
            respondWithSpeech(text)
        } else {
            respondWithSpeech("Your notification tray is currently clear, Sir.")
        }
    }

    private fun speakProactiveAlert(alertText: String) {
        if (_orbState.value == OrbState.STANDBY) {
            _assistantResponseText.value = alertText
            _orbState.value = OrbState.SPEAKING
            _statusNotice.value = "Proactive Notification"
            ttsManager.speak(alertText) {
                _orbState.value = OrbState.STANDBY
                _statusNotice.value = "Standing by"
            }
        }
    }

    fun saveApiKey(key: String) {
        keystoreManager.saveApiKey(key)
        _isApiKeyConfigured.value = true
        _currentMaskedApiKey.value = keystoreManager.getMaskedApiKey()
        testAiConnection()
    }

    fun removeApiKey() {
        keystoreManager.removeApiKey()
        _isApiKeyConfigured.value = false
        _currentMaskedApiKey.value = ""
        _connectionStatus.value = ConnectionTestResult.NotConfigured
    }

    fun testAiConnection() {
        _connectionStatus.value = ConnectionTestResult.Testing
        viewModelScope.launch {
            val result = aiProvider.testConnection()
            _connectionStatus.value = result
        }
    }

    // Memory operations
    fun saveMemory(key: String, content: String) {
        viewModelScope.launch {
            repository.saveMemory(key, content)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
        }
    }

    // Tasks operations
    fun addTask(title: String, priority: String) {
        viewModelScope.launch {
            repository.addTask(title, priority = priority)
        }
    }

    fun toggleTaskCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.toggleTaskCompleted(id, completed)
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            repository.deleteTask(id)
        }
    }

    // Reminder operations
    fun addReminder(title: String, timeOffsetMinutes: Int) {
        viewModelScope.launch {
            val trigger = System.currentTimeMillis() + (timeOffsetMinutes * 60 * 1000L)
            repository.addReminder(title, trigger)
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            repository.deleteReminder(id)
        }
    }

    // Automation & Modes
    fun toggleFocusMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setFocusMode(enabled)
            if (enabled) preferencesRepository.setStudyMode(false)
        }
    }

    fun toggleStudyMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setStudyMode(enabled)
            if (enabled) preferencesRepository.setFocusMode(false)
        }
    }

    fun updateProactiveMode(mode: ProactiveMode) {
        viewModelScope.launch {
            preferencesRepository.updateProactiveMode(mode)
        }
    }

    fun updateProactiveCooldown(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.updateProactiveCooldown(minutes)
        }
    }

    fun updateQuietHours(enabled: Boolean, start: String, end: String) {
        viewModelScope.launch {
            preferencesRepository.updateQuietHours(enabled, start, end)
        }
    }

    fun updateResponseMode(mode: ResponseMode) {
        viewModelScope.launch {
            preferencesRepository.updateResponseMode(mode)
        }
    }

    fun updateAssistantName(name: String) {
        viewModelScope.launch {
            preferencesRepository.updateAssistantName(name)
        }
    }

    fun setContinuousListening(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setContinuousListening(enabled)
        }
    }

    // Voice synthesizer settings
    fun updateSpeechSettings(speed: Float, pitch: Float, language: String) {
        viewModelScope.launch {
            preferencesRepository.updateSpeechSettings(speed, pitch, language)
            ttsManager.updateSettings(speed, pitch, language)
        }
    }

    fun testVoiceSample(sampleText: String) {
        ttsManager.speak(sampleText)
    }

    private fun applyCurrentSpeechSettings() {
        val s = settings.value
        ttsManager.updateSettings(s.speechSpeed, s.speechPitch, s.speechLanguage)
    }

    // History
    fun clearActionHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Special Access & Permissions Manager
    fun refreshSpecialAccesses() {
        _specialAccessList.value = specialAccessManager.evaluateAllAccesses()
    }

    fun openSpecialAccess(item: SpecialAccessItem): Boolean {
        return specialAccessManager.openSettingsForAccess(item)
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
        speechRecognizer.destroy()
        proactiveLoopJob?.cancel()
    }
}
