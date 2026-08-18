package com.example.commands

import com.example.ai.AiProvider
import com.example.ai.ChatMessage
import com.example.core.datastore.JarvisPreferencesRepository
import com.example.data.repository.JarvisRepository
import com.example.device.ActionResult
import com.example.device.CommunicationManager
import com.example.device.CommunicationType
import com.example.device.DeviceActionExecutor
import com.example.device.PendingCommunicationAction
import com.example.services.JarvisAccessibilityService
import com.example.services.JarvisNotificationListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class RouteResult {
    data class SpokenResponse(
        val text: String,
        val isSuccess: Boolean = true,
        val actionType: String = "LOCAL_COMMAND"
    ) : RouteResult()

    data class ActionExecuted(
        val message: String,
        val actionType: String,
        val isSuccess: Boolean = true
    ) : RouteResult()

    data class ConfirmationRequired(
        val action: PendingCommunicationAction,
        val promptText: String
    ) : RouteResult()

    data class AiGenerated(val text: String) : RouteResult()
    data class Error(val message: String) : RouteResult()
}

class CommandRouter(
    private val deviceExecutor: DeviceActionExecutor,
    private val communicationManager: CommunicationManager,
    private val repository: JarvisRepository,
    private val preferencesRepository: JarvisPreferencesRepository,
    private val aiProvider: AiProvider
) {

    suspend fun processCommand(
        rawCommand: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): RouteResult = withContext(Dispatchers.IO) {
        val trimmed = rawCommand.trim()
        if (trimmed.isBlank()) {
            return@withContext RouteResult.Error("No command provided.")
        }

        // Multi-step command handling (e.g. "open youtube and lower the volume")
        if (trimmed.contains(" and ", ignoreCase = true) || trimmed.contains(" then ", ignoreCase = true) || trimmed.contains(" aur ", ignoreCase = true)) {
            val delimiter = when {
                trimmed.contains(" and ", ignoreCase = true) -> " and "
                trimmed.contains(" then ", ignoreCase = true) -> " then "
                else -> " aur "
            }
            val parts = trimmed.split(Regex(delimiter, RegexOption.IGNORE_CASE))
            if (parts.size in 2..3 && parts.all { isLikelyLocalCommand(it) }) {
                val results = mutableListOf<String>()
                var allSuccess = true
                for (part in parts) {
                    val subResult = executeSingleLocalCommand(part.trim())
                    when (subResult) {
                        is RouteResult.SpokenResponse -> {
                            results.add(subResult.text)
                            if (!subResult.isSuccess) allSuccess = false
                        }
                        is RouteResult.ConfirmationRequired -> {
                            return@withContext subResult
                        }
                        else -> {}
                    }
                }
                if (results.isNotEmpty()) {
                    val combinedText = results.joinToString(" Also, ")
                    repository.logAction(trimmed, "MULTI_STEP", combinedText, allSuccess)
                    return@withContext RouteResult.SpokenResponse(combinedText, allSuccess, "MULTI_STEP")
                }
            }
        }

        // Check single local command first (device actions, hardware, screen, notifications, communication)
        val localResult = executeSingleLocalCommand(trimmed)
        if (localResult != null) {
            when (localResult) {
                is RouteResult.SpokenResponse -> repository.logAction(trimmed, localResult.actionType, localResult.text, localResult.isSuccess)
                is RouteResult.ConfirmationRequired -> repository.logAction(trimmed, "CONFIRMATION_PENDING", localResult.promptText, true)
                else -> {}
            }
            return@withContext localResult
        }

        // Route to AI Brain
        val memories = repository.getRecentApprovedMemories().map { "${it.key}: ${it.content}" }
        val aiResult = aiProvider.generateResponse(
            prompt = trimmed,
            conversationHistory = conversationHistory,
            memories = memories
        )

        return@withContext if (aiResult.isSuccess) {
            val answer = aiResult.getOrDefault("I am unable to formulate a response at this moment.")
            repository.logAction(trimmed, "AI_REASONING", answer, true)
            RouteResult.AiGenerated(answer)
        } else {
            // Intelligent offline fallback response
            val offlineFallback = generateOfflineFallbackResponse(trimmed)
            repository.logAction(trimmed, "OFFLINE_ASSISTANT", offlineFallback, true)
            RouteResult.SpokenResponse(offlineFallback, isSuccess = true, actionType = "OFFLINE_FALLBACK")
        }
    }

    private fun isLikelyLocalCommand(cmd: String): Boolean {
        val lower = cmd.lowercase().trim()
        val localTriggers = listOf(
            "battery", "power", "volume", "sound", "mute", "open", "launch", "flashlight",
            "torch", "focus mode", "study mode", "task", "reminder", "alarm", "timer",
            "time", "date", "briefing", "remember that", "what do you remember", "who are you",
            "screen", "notification", "call", "phone", "message", "sms", "storage", "specs", "device info",
            "aawaz", "baje", "karo", "lagao", "padho"
        )
        return localTriggers.any { lower.contains(it) }
    }

    private suspend fun executeSingleLocalCommand(cmd: String): RouteResult? {
        val lower = cmd.lowercase().trim()

        // 1. Identity & System Status
        if (lower == "who are you" || lower == "what is your name" || lower == "introduce yourself" || lower == "aap kaun ho" || lower == "tum kaun ho") {
            return RouteResult.SpokenResponse("I am J.A.R.V.I.S, Just A Rather Very Intelligent System. Your on-device neural assistant standing by for instructions.")
        }
        if (lower == "status" || lower == "system status" || lower == "report" || lower == "system report") {
            val battery = deviceExecutor.getBatteryStatus()
            val storage = deviceExecutor.getStorageStats()
            val net = deviceExecutor.getNetworkStatus()
            val pendingTasks = repository.getPendingTasksSync().size
            return RouteResult.SpokenResponse("All systems operational. $battery Storage: ${storage.freeGb} GB free of ${storage.totalGb} GB. $net You have $pendingTasks active tasks.")
        }

        // 2. Battery & Power
        if (lower.contains("battery") || lower.contains("power level") || lower.contains("charge percentage") || lower.contains("battery kitni hai")) {
            val batteryText = deviceExecutor.getBatteryStatus()
            return RouteResult.SpokenResponse(batteryText)
        }

        // 3. Storage & Device Hardware Specs
        if (lower.contains("storage") || lower.contains("disk space") || lower.contains("memory space") || lower.contains("storage kitni bachi")) {
            val storage = deviceExecutor.getStorageStats()
            return RouteResult.SpokenResponse("Internal storage: ${storage.freeGb} GB available out of ${storage.totalGb} GB (${storage.usedPercent}% used).")
        }
        if (lower.contains("device info") || lower.contains("device specs") || lower.contains("hardware info") || lower.contains("system info") || lower.contains("phone details")) {
            val specs = deviceExecutor.getDeviceSpecs()
            return RouteResult.SpokenResponse("Device: ${specs.manufacturer} ${specs.model} running Android ${specs.androidVersion} (API ${specs.sdkInt}). Architecture: ${specs.supportedAbis}.")
        }
        if (lower.contains("network status") || lower.contains("internet status") || lower.contains("wifi status") || lower.contains("internet chal raha hai")) {
            val net = deviceExecutor.getNetworkStatus()
            return RouteResult.SpokenResponse(net)
        }

        // 4. Volume & Audio Control
        if (lower.contains("volume up") || lower.contains("raise volume") || lower.contains("increase volume") || lower.contains("louder") || lower.contains("aawaz badhao")) {
            val res = deviceExecutor.adjustVolume(increase = true)
            val msg = if (res is ActionResult.Success) res.message else "Unable to adjust volume."
            return RouteResult.SpokenResponse(msg)
        }
        if (lower.contains("volume down") || lower.contains("lower volume") || lower.contains("decrease volume") || lower.contains("softer") || lower.contains("aawaz kam karo")) {
            val res = deviceExecutor.adjustVolume(increase = false)
            val msg = if (res is ActionResult.Success) res.message else "Unable to adjust volume."
            return RouteResult.SpokenResponse(msg)
        }
        if (lower.contains("mute volume") || lower.contains("silence media") || lower == "mute" || lower.contains("aawaz band karo")) {
            val res = deviceExecutor.muteVolume()
            val msg = if (res is ActionResult.Success) res.message else "Unable to mute volume."
            return RouteResult.SpokenResponse(msg)
        }

        // 5. Flashlight
        if (lower.contains("flashlight on") || lower.contains("torch on") || lower.contains("turn on flashlight") || lower.contains("turn on torch") || lower.contains("flashlight jalao") || lower.contains("torch chalu karo")) {
            val res = deviceExecutor.toggleFlashlight(true)
            val msg = if (res is ActionResult.Success) res.message else "Flashlight is unavailable."
            return RouteResult.SpokenResponse(msg)
        }
        if (lower.contains("flashlight off") || lower.contains("torch off") || lower.contains("turn off flashlight") || lower.contains("turn off torch") || lower.contains("flashlight band karo")) {
            val res = deviceExecutor.toggleFlashlight(false)
            val msg = if (res is ActionResult.Success) res.message else "Flashlight is unavailable."
            return RouteResult.SpokenResponse(msg)
        }

        // 6. Settings Shortcuts
        if (lower.contains("open wifi") || lower.contains("wifi setting") || lower.contains("wifi settings")) {
            val res = deviceExecutor.openWifiSettings()
            return RouteResult.SpokenResponse(if (res is ActionResult.Success) res.message else "Opening Wi-Fi settings.")
        }
        if (lower.contains("open bluetooth") || lower.contains("bluetooth setting") || lower.contains("bluetooth settings")) {
            val res = deviceExecutor.openBluetoothSettings()
            return RouteResult.SpokenResponse(if (res is ActionResult.Success) res.message else "Opening Bluetooth settings.")
        }
        if (lower.contains("open display") || lower.contains("brightness setting") || lower.contains("display settings")) {
            val res = deviceExecutor.openDisplaySettings()
            return RouteResult.SpokenResponse(if (res is ActionResult.Success) res.message else "Opening Display settings.")
        }
        if (lower.contains("open sound") || lower.contains("sound setting") || lower.contains("sound settings")) {
            val res = deviceExecutor.openSoundSettings()
            return RouteResult.SpokenResponse(if (res is ActionResult.Success) res.message else "Opening Sound settings.")
        }
        if (lower.contains("open storage setting") || lower.contains("storage settings")) {
            val res = deviceExecutor.openStorageSettings()
            return RouteResult.SpokenResponse(if (res is ActionResult.Success) res.message else "Opening Storage settings.")
        }
        if (lower.contains("open app setting") || lower.contains("apps setting") || lower.contains("installed apps")) {
            val res = deviceExecutor.openApplicationSettings()
            return RouteResult.SpokenResponse(if (res is ActionResult.Success) res.message else "Opening Installed Apps manager.")
        }

        // 7. Screen Assistant (Accessibility Neural Link)
        if (lower.contains("read screen") || lower.contains("what's on my screen") || lower.contains("what is on my screen") ||
            lower.contains("explain screen") || lower.contains("screen pe kya hai") || lower.contains("screen padho") || lower.contains("explain this screen")) {
            if (!JarvisAccessibilityService.isRunning) {
                return RouteResult.SpokenResponse("Accessibility Neural Link is not enabled. Please enable J.A.R.V.I.S in Settings > Special Access > Accessibility.")
            }
            val screenContext = JarvisAccessibilityService.getScreenContext()
            return if (screenContext != null && screenContext.visibleTexts.isNotEmpty()) {
                val preview = screenContext.visibleTexts.take(5).joinToString(", ")
                RouteResult.SpokenResponse("On-screen content from ${screenContext.activePackage}: $preview.")
            } else {
                RouteResult.SpokenResponse("Accessibility link is active, but no clear text elements are detectable on the current screen.")
            }
        }

        // 8. Notification Assistant
        if (lower.contains("read notification") || lower.contains("check notification") || lower.contains("summarize notification") ||
            lower.contains("notifications kya hain") || lower.contains("koi message aaya kya") || lower.contains("koi notification hai kya")) {
            if (!JarvisNotificationListenerService.isConnected) {
                return RouteResult.SpokenResponse("Notification Listener access is not granted. Please enable it in Settings > Special Access > Notification Access.")
            }
            val notifs = JarvisNotificationListenerService.getRecentNotifications()
            return if (notifs.isNotEmpty()) {
                val summary = notifs.take(3).joinToString("; ") { "${it.appLabel}: ${it.title} - ${it.text}" }
                RouteResult.SpokenResponse("You have ${notifs.size} recent notifications: $summary.")
            } else {
                RouteResult.SpokenResponse("Your notification center is clear. No unread alerts.")
            }
        }

        // 9. Communication Assistant (Contacts & Phone Calls with Confirmation)
        if (lower.startsWith("call ") || lower.startsWith("phone lagao ") || lower.startsWith("call karo ")) {
            val contactQuery = cmd.substringAfter("call").substringAfter("phone lagao").substringAfter("call karo").trim()
            if (contactQuery.isNotBlank()) {
                val matchingContacts = communicationManager.searchContacts(contactQuery)
                val (targetName, targetNumber) = if (matchingContacts.isNotEmpty()) {
                    Pair(matchingContacts.first().name, matchingContacts.first().phoneNumber)
                } else {
                    Pair(contactQuery, contactQuery)
                }

                val action = PendingCommunicationAction(
                    type = CommunicationType.PHONE_CALL,
                    targetName = targetName,
                    targetNumber = targetNumber,
                    actionSummary = "Call $targetName ($targetNumber)"
                )
                return RouteResult.ConfirmationRequired(
                    action = action,
                    promptText = "Would you like me to place a call to $targetName ($targetNumber)? Please confirm."
                )
            }
        }

        // 10. Communication Assistant (SMS Messaging with Confirmation)
        if (lower.startsWith("send message to ") || lower.startsWith("message ") || lower.startsWith("sms ") || lower.startsWith("sandesh bhejo ")) {
            val afterTrigger = cmd.substringAfter("send message to").substringAfter("message").substringAfter("sms").substringAfter("sandesh bhejo").trim()
            val targetName = afterTrigger.substringBefore(" saying ").substringBefore(" text ").substringBefore(" ko ").trim()
            val messageBody = if (afterTrigger.contains(" saying ")) afterTrigger.substringAfter(" saying ")
            else if (afterTrigger.contains(" text ")) afterTrigger.substringAfter(" text ")
            else if (afterTrigger.contains(" ko ")) afterTrigger.substringAfter(" ko ")
            else "Hello from J.A.R.V.I.S"

            if (targetName.isNotBlank()) {
                val matchingContacts = communicationManager.searchContacts(targetName)
                val (contactName, targetNumber) = if (matchingContacts.isNotEmpty()) {
                    Pair(matchingContacts.first().name, matchingContacts.first().phoneNumber)
                } else {
                    Pair(targetName, targetName)
                }

                val action = PendingCommunicationAction(
                    type = CommunicationType.SEND_SMS,
                    targetName = contactName,
                    targetNumber = targetNumber,
                    messageBody = messageBody,
                    actionSummary = "Send message to $contactName: \"$messageBody\""
                )
                return RouteResult.ConfirmationRequired(
                    action = action,
                    promptText = "Confirm sending message to $contactName: \"$messageBody\"?"
                )
            }
        }

        // 11. Focus Mode & Study Mode
        if (lower.contains("start focus mode") || lower.contains("enable focus mode") || lower.contains("activate focus mode") || lower.contains("focus mode chalu karo")) {
            preferencesRepository.setFocusMode(true)
            return RouteResult.SpokenResponse("Focus mode activated. Distractions minimized.")
        }
        if (lower.contains("stop focus mode") || lower.contains("disable focus mode") || lower.contains("deactivate focus mode") || lower.contains("focus mode band karo")) {
            preferencesRepository.setFocusMode(false)
            return RouteResult.SpokenResponse("Focus mode deactivated. Standard telemetry restored.")
        }
        if (lower.contains("start study mode") || lower.contains("enable study mode") || lower.contains("padhai mode")) {
            preferencesRepository.setStudyMode(true)
            return RouteResult.SpokenResponse("Study mode engaged. Standing by for academic problem solving.")
        }
        if (lower.contains("stop study mode") || lower.contains("disable study mode")) {
            preferencesRepository.setStudyMode(false)
            return RouteResult.SpokenResponse("Study mode disengaged.")
        }

        // 12. Time & Date
        if (lower == "what time is it" || lower == "time" || lower == "current time" || lower.contains("samay kya hua") || lower.contains("time kya hai")) {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            return RouteResult.SpokenResponse("The current time is $time.")
        }
        if (lower == "what is today's date" || lower == "today's date" || lower == "what day is it" || lower.contains("aaj konsi tarikh")) {
            val date = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
            return RouteResult.SpokenResponse("Today is $date.")
        }

        // 13. Alarms & Timers
        if (lower.startsWith("set alarm for ") || lower.startsWith("alarm for ") || lower.startsWith("alarm lagao ")) {
            val timeStr = lower.substringAfter("alarm for").substringAfter("alarm lagao").substringAfter("alarm").trim()
            val parsedTime = parseTimeDigits(timeStr)
            if (parsedTime != null) {
                val res = deviceExecutor.createSystemAlarm(parsedTime.first, parsedTime.second, "JARVIS Alarm")
                return RouteResult.SpokenResponse(if (res is ActionResult.Success) res.message else "Alarm registered.")
            }
        }
        if (lower.startsWith("set timer for ") || lower.startsWith("timer for ") || lower.startsWith("timer lagao ")) {
            val timerStr = lower.substringAfter("timer for").substringAfter("timer lagao").substringAfter("timer").trim()
            val minutes = parseTimerMinutes(timerStr)
            if (minutes > 0) {
                val res = deviceExecutor.setSystemTimer(minutes * 60, "JARVIS Timer")
                return RouteResult.SpokenResponse(if (res is ActionResult.Success) res.message else "Timer started for $minutes minutes.")
            }
        }

        // 14. Daily Briefing
        if (lower.contains("briefing") || lower.contains("daily summary") || lower == "what do i have to do today" || lower == "aaj ka plan kya hai") {
            val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
            val pendingTasks = repository.getPendingTasksSync()
            val upcomingReminders = repository.getUpcomingRemindersSync()
            val battery = deviceExecutor.getBatteryPercentage()
            val storage = deviceExecutor.getStorageStats()

            val taskSummary = if (pendingTasks.isNotEmpty()) {
                "You have ${pendingTasks.size} pending tasks: '${pendingTasks.first().title}'."
            } else {
                "You have no pending tasks."
            }

            val reminderSummary = if (upcomingReminders.isNotEmpty()) {
                "${upcomingReminders.size} scheduled reminders."
            } else {
                "No urgent reminders."
            }

            val text = "Good day. Today is $date. $taskSummary $reminderSummary Battery is at $battery%, Storage: ${storage.freeGb} GB free."
            return RouteResult.SpokenResponse(text)
        }

        // 15. Memory Management via Voice
        if (lower.startsWith("remember that ") || lower.startsWith("remember ") || lower.startsWith("yaad rakhna ki ") || lower.startsWith("yaad rakho ")) {
            val memoryContent = cmd.substringAfter("remember", "").substringAfter("yaad", "")
                .replaceFirst("that", "").replaceFirst("rakhna ki", "").replaceFirst("rakho", "").trim()
            if (memoryContent.isNotBlank()) {
                val key = memoryContent.take(24)
                repository.saveMemory(key = key, content = memoryContent, category = "VOICE_MEMORY")
                return RouteResult.SpokenResponse("Committed to long-term memory: \"$memoryContent\".")
            }
        }
        if (lower.startsWith("what do you remember about ") || lower.startsWith("what do you know about ") || lower.startsWith("mere baare me kya pata hai")) {
            val query = lower.substringAfter("about", "").trim()
            val memories = repository.getRecentApprovedMemories().filter {
                query.isBlank() || it.key.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)
            }
            return if (memories.isNotEmpty()) {
                val found = memories.take(3).joinToString("; ") { it.content }
                RouteResult.SpokenResponse("Here are your stored memories: $found")
            } else {
                RouteResult.SpokenResponse("No recorded memories found matching '$query'.")
            }
        }

        // 16. Tasks & Reminders via Voice
        if (lower.startsWith("add task ") || lower.startsWith("create task ") || lower.startsWith("new task ") || lower.startsWith("task jodo ")) {
            val title = cmd.substringAfter("task").substringAfter("jodo").trim()
            if (title.isNotBlank()) {
                repository.addTask(title = title)
                return RouteResult.SpokenResponse("Task registered: $title.")
            }
        }
        if (lower == "what are my tasks" || lower == "list tasks" || lower == "show tasks" || lower == "mere tasks kya hain") {
            val tasks = repository.getPendingTasksSync()
            return if (tasks.isNotEmpty()) {
                val list = tasks.take(4).joinToString(", ") { it.title }
                RouteResult.SpokenResponse("You have ${tasks.size} pending tasks: $list.")
            } else {
                RouteResult.SpokenResponse("Task list is clear. No pending tasks.")
            }
        }
        if (lower.startsWith("set reminder ") || lower.startsWith("remind me to ") || lower.startsWith("remind me ") || lower.startsWith("yaad dilana ")) {
            val title = cmd.substringAfter("remind").substringAfter("yaad dilana").replaceFirst("me to", "").replaceFirst("me", "").trim()
            if (title.isNotBlank()) {
                val triggerTime = System.currentTimeMillis() + (60 * 60 * 1000L)
                repository.addReminder(title = title, triggerTimeMillis = triggerTime)
                return RouteResult.SpokenResponse("Reminder set: $title.")
            }
        }

        // 17. App Launching
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("chalu karo ")) {
            val appName = cmd.substringAfter("open").substringAfter("launch").substringAfter("chalu karo").trim()
            if (appName.isNotBlank()) {
                val result = deviceExecutor.launchApplicationByName(appName)
                val msg = when (result) {
                    is ActionResult.Success -> result.message
                    is ActionResult.HandledWithIntent -> result.message
                    is ActionResult.Failure -> result.reason
                    else -> "Opening $appName."
                }
                return RouteResult.SpokenResponse(msg, isSuccess = result !is ActionResult.Failure)
            }
        }

        return null
    }

    private fun parseTimeDigits(timeStr: String): Pair<Int, Int>? {
        val clean = timeStr.lowercase().trim()
        val isPm = clean.contains("pm") || clean.contains("shaam") || clean.contains("raat")
        val isAm = clean.contains("am") || clean.contains("subah")

        val digits = Regex("\\d+").findAll(clean).map { it.value.toInt() }.toList()
        if (digits.isEmpty()) return null

        var hour = digits[0]
        val minute = if (digits.size > 1) digits[1] else 0

        if (isPm && hour < 12) hour += 12
        if (isAm && hour == 12) hour = 0

        return Pair(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    private fun parseTimerMinutes(timerStr: String): Int {
        val clean = timerStr.lowercase().trim()
        val digits = Regex("\\d+").find(clean)?.value?.toIntOrNull() ?: return 5
        return if (clean.contains("second") || clean.contains("sec")) {
            (digits / 60).coerceAtLeast(1)
        } else if (clean.contains("hour") || clean.contains("ghanta")) {
            digits * 60
        } else {
            digits
        }
    }

    private fun generateOfflineFallbackResponse(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("namaste") ->
                "Hello, Sir. J.A.R.V.I.S is operating in high-performance local mode. How may I assist you?"
            lower.contains("thank you") || lower.contains("thanks") || lower.contains("shukriya") ->
                "At your service, Sir. Always."
            lower.contains("translate") ->
                "To translate complex text with full linguistic nuance, please ensure internet connectivity or configure your Gemini API key in Settings."
            lower.contains("calculate") || lower.contains("+") || lower.contains("-") || lower.contains("*") || lower.contains("/") ->
                "Local mathematical evaluation: Please formulate mathematical expressions clearly for step-by-step reasoning."
            else ->
                "I have processed your query locally: \"$prompt\". All local device assistance, tasks, alarms, and hardware controls remain fully operational."
        }
    }
}
