package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.GeminiAiProvider
import com.example.commands.CommandRouter
import com.example.commands.RouteResult
import com.example.core.datastore.JarvisPreferencesRepository
import com.example.core.security.KeystoreManager
import com.example.data.local.db.JarvisDatabase
import com.example.data.repository.JarvisRepository
import com.example.device.CommunicationManager
import com.example.device.DeviceActionExecutor
import com.example.voice.speech.JarvisSpeechRecognizer
import com.example.voice.tts.JarvisTtsManager
import com.example.voice.wakeword.JarvisWakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class JarvisVoiceService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var wakeWordEngine: JarvisWakeWordEngine? = null
    private var speechRecognizer: JarvisSpeechRecognizer? = null
    private var ttsManager: JarvisTtsManager? = null
    private var commandRouter: CommandRouter? = null
    private var preferencesRepository: JarvisPreferencesRepository? = null

    private var isListeningForCommand = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating J.A.R.V.I.S Background Voice Service...")

        val keystoreManager = KeystoreManager(applicationContext)
        val database = JarvisDatabase.getInstance(applicationContext)
        val repository = JarvisRepository(database)
        val deviceExecutor = DeviceActionExecutor(applicationContext)
        val communicationManager = CommunicationManager(applicationContext)
        val aiProvider = GeminiAiProvider(keystoreManager)
        preferencesRepository = JarvisPreferencesRepository(applicationContext)

        commandRouter = CommandRouter(
            deviceExecutor = deviceExecutor,
            communicationManager = communicationManager,
            repository = repository,
            preferencesRepository = preferencesRepository!!,
            aiProvider = aiProvider
        )

        ttsManager = JarvisTtsManager(applicationContext)

        speechRecognizer = JarvisSpeechRecognizer(
            context = applicationContext,
            onResult = { spokenText -> handleCommandResult(spokenText) },
            onError = { error -> handleRecognitionError(error) }
        )

        wakeWordEngine = JarvisWakeWordEngine(applicationContext)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP_SERVICE -> {
                Log.i(TAG, "Stopping J.A.R.V.I.S Background Voice Service")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_LISTEN_NOW -> {
                startActiveListening()
                return START_STICKY
            }
        }

        startForegroundWithNotification("J.A.R.V.I.S Neural Link: Active", "Listening for 'JARVIS'...")
        isRunning = true
        _serviceActiveState.value = true

        startWakeWordSpotting()

        return START_STICKY
    }

    private fun startWakeWordSpotting() {
        isListeningForCommand = false
        wakeWordEngine?.startWakeWordDetection { detectedPhrase ->
            Log.i(TAG, "Wake word triggered: $detectedPhrase")
            vibrateOnWake()
            ttsManager?.speak("Yes, Sir?") {
                startActiveListening()
            }
        }
        updateNotification("J.A.R.V.I.S Neural Link: Active", "Listening for wake word 'JARVIS'...")
    }

    private fun startActiveListening() {
        wakeWordEngine?.stopWakeWordDetection()
        isListeningForCommand = true
        updateNotification("J.A.R.V.I.S Neural Link: Listening", "Listening for command...")

        serviceScope.launch {
            val settings = preferencesRepository?.settingsFlow?.first()
            val language = settings?.speechLanguage ?: "en-US"
            speechRecognizer?.startListening(language)
        }
    }

    private fun handleCommandResult(spokenText: String) {
        isListeningForCommand = false
        val cleanCommand = JarvisWakeWordEngine.stripWakeWordPrefix(spokenText)
        updateNotification("J.A.R.V.I.S Neural Link: Processing", "“$cleanCommand”")

        serviceScope.launch {
            val result = commandRouter?.processCommand(cleanCommand)
            when (result) {
                is RouteResult.SpokenResponse -> {
                    speakAndResume(result.text)
                }
                is RouteResult.AiGenerated -> {
                    speakAndResume(result.text)
                }
                is RouteResult.ActionExecuted -> {
                    speakAndResume(result.message)
                }
                is RouteResult.ConfirmationRequired -> {
                    speakAndResume(result.promptText)
                }
                is RouteResult.Error -> {
                    speakAndResume(result.message)
                }
                null -> {
                    startWakeWordSpotting()
                }
            }
        }
    }

    private fun handleRecognitionError(error: String) {
        Log.w(TAG, "Command recognition error: $error")
        isListeningForCommand = false
        serviceScope.launch {
            delay(500)
            startWakeWordSpotting()
        }
    }

    private fun speakAndResume(text: String) {
        updateNotification("J.A.R.V.I.S Neural Link: Responding", text.take(60))
        ttsManager?.speak(text) {
            serviceScope.launch {
                val settings = preferencesRepository?.settingsFlow?.first()
                if (settings?.continuousListeningEnabled == true) {
                    delay(400)
                    startActiveListening()
                } else {
                    delay(300)
                    startWakeWordSpotting()
                }
            }
        }
    }

    private fun vibrateOnWake() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(80)
                }
            }
        } catch (e: Exception) {
            // Ignore vibration error
        }
    }

    private fun startForegroundWithNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val listenIntent = Intent(this, JarvisVoiceService::class.java).apply {
            action = ACTION_LISTEN_NOW
        }
        val listenPendingIntent = PendingIntent.getService(
            this, 1, listenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JarvisVoiceService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_btn_speak_now, "Listen Now", listenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "J.A.R.V.I.S Voice Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps J.A.R.V.I.S wake-word listening active in background"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Destroying J.A.R.V.I.S Voice Service")
        isRunning = false
        _serviceActiveState.value = false
        wakeWordEngine?.stopWakeWordDetection()
        speechRecognizer?.destroy()
        ttsManager?.shutdown()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "JarvisVoiceService"
        const val CHANNEL_ID = "jarvis_voice_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.example.action.START_VOICE_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_VOICE_SERVICE"
        const val ACTION_LISTEN_NOW = "com.example.action.LISTEN_NOW"

        var isRunning: Boolean = false
            private set

        private val _serviceActiveState = MutableStateFlow(false)
        val serviceActiveState = _serviceActiveState.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun triggerListenNow(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_LISTEN_NOW
            }
            context.startService(intent)
        }
    }
}
