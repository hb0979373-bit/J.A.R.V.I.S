package com.example.proactive

import com.example.core.datastore.JarvisPreferencesRepository
import com.example.core.datastore.JarvisSettings
import com.example.core.datastore.ProactiveMode
import com.example.data.repository.JarvisRepository
import com.example.device.DeviceActionExecutor
import java.util.Calendar

class ProactiveEngine(
    private val preferencesRepository: JarvisPreferencesRepository,
    private val repository: JarvisRepository,
    private val deviceExecutor: DeviceActionExecutor,
    private val onSpeakAlert: (String) -> Unit
) {

    private var lastSpokenTimestamp: Long = 0L
    private var lastBatteryWarningPct: Int = 100

    suspend fun evaluateProactiveTriggers(settings: JarvisSettings) {
        if (settings.proactiveMode == ProactiveMode.OFF) return
        if (settings.focusModeEnabled) return // Smart silence during focus
        if (isQuietHours(settings)) return // Smart silence during quiet hours

        val now = System.currentTimeMillis()
        val cooldownMs = settings.proactiveCooldownMinutes * 60 * 1000L
        if (now - lastSpokenTimestamp < cooldownMs) return

        // 1. Check Due Reminders
        val dueReminders = repository.getDueReminders(now)
        if (dueReminders.isNotEmpty()) {
            val first = dueReminders.first()
            repository.markReminderTriggered(first.id)
            lastSpokenTimestamp = now
            onSpeakAlert("Pardon the interruption. You have a scheduled reminder: ${first.title}.")
            return
        }

        // 2. Check Low Battery Warning (if < 20% and hasn't warned at this level yet)
        val batteryPct = deviceExecutor.getBatteryPercentage()
        if (batteryPct <= 20 && batteryPct < lastBatteryWarningPct) {
            lastBatteryWarningPct = batteryPct
            lastSpokenTimestamp = now
            onSpeakAlert("Sir, power levels are decreasing. Device battery is at $batteryPct%.")
            return
        } else if (batteryPct > 30) {
            lastBatteryWarningPct = 100 // reset threshold when recharged
        }
    }

    private fun isQuietHours(settings: JarvisSettings): Boolean {
        if (!settings.quietHoursEnabled) return false
        try {
            val cal = Calendar.getInstance()
            val currentHour = cal.get(Calendar.HOUR_OF_DAY)
            val currentMin = cal.get(Calendar.MINUTE)
            val currentTotal = currentHour * 60 + currentMin

            val startParts = settings.quietHoursStart.split(":").map { it.toInt() }
            val startTotal = startParts[0] * 60 + startParts[1]

            val endParts = settings.quietHoursEnd.split(":").map { it.toInt() }
            val endTotal = endParts[0] * 60 + endParts[1]

            return if (startTotal > endTotal) {
                // e.g. 22:00 -> 07:00 (spans midnight)
                currentTotal >= startTotal || currentTotal < endTotal
            } else {
                currentTotal in startTotal until endTotal
            }
        } catch (e: Exception) {
            return false
        }
    }
}
