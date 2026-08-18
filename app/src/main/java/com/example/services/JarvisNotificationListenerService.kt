package com.example.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

data class InterceptedNotification(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val timestampMillis: Long
)

class JarvisNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        isConnected = true
        Log.i(TAG, "J.A.R.V.I.S Notification Listener matrix connected.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        isConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return
        // Filter out ongoing background service indicators
        if (sbn.isOngoing) return

        val extras = sbn.notification?.extras
        val title = extras?.getString("android.title") ?: extras?.getCharSequence("android.title")?.toString() ?: ""
        val text = extras?.getCharSequence("android.text")?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appLabel = runCatching {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))

        val notif = InterceptedNotification(
            id = "${sbn.id}_${sbn.postTime}",
            packageName = packageName,
            appLabel = appLabel,
            title = title,
            text = text,
            timestampMillis = sbn.postTime
        )

        notificationBuffer.add(0, notif)
        while (notificationBuffer.size > 20) {
            notificationBuffer.removeAt(notificationBuffer.lastIndex)
        }
        Log.d(TAG, "Notification stored: [$appLabel] $title: $text")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        notificationBuffer.removeAll { it.id.startsWith("${sbn.id}_") }
    }

    companion object {
        private const val TAG = "JarvisNotification"
        var isConnected: Boolean = false
            private set
        private var instance: JarvisNotificationListenerService? = null
        private val notificationBuffer = CopyOnWriteArrayList<InterceptedNotification>()

        fun getRecentNotifications(): List<InterceptedNotification> {
            return notificationBuffer.toList()
        }

        fun clearNotifications() {
            notificationBuffer.clear()
        }
    }
}

