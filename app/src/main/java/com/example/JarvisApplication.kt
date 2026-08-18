package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

class JarvisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        Log.d(TAG, "J.A.R.V.I.S Neural Core Initialized.")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            val proactiveChannel = NotificationChannel(
                CHANNEL_PROACTIVE,
                "J.A.R.V.I.S Proactive Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Proactive status briefings, security telemetry, and battery alerts"
                enableVibration(true)
            }

            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "J.A.R.V.I.S Reminders & Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timed task reminders and scheduled intelligence alerts"
                enableVibration(true)
            }

            notificationManager?.createNotificationChannel(proactiveChannel)
            notificationManager?.createNotificationChannel(remindersChannel)
        }
    }

    companion object {
        private const val TAG = "JarvisApplication"
        const val CHANNEL_PROACTIVE = "jarvis_proactive_channel"
        const val CHANNEL_REMINDERS = "jarvis_reminders_channel"
    }
}
