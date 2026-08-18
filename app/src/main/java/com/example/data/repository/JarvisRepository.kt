package com.example.data.repository

import com.example.data.local.db.JarvisDatabase
import com.example.data.local.db.entity.ActionHistoryItem
import com.example.data.local.db.entity.AutomationRule
import com.example.data.local.db.entity.JarvisReminder
import com.example.data.local.db.entity.JarvisTask
import com.example.data.local.db.entity.MemoryItem
import kotlinx.coroutines.flow.Flow

class JarvisRepository(private val database: JarvisDatabase) {

    // Memory operations
    val allMemories: Flow<List<MemoryItem>> = database.memoryDao().getAllMemories()
    fun searchMemories(query: String): Flow<List<MemoryItem>> = database.memoryDao().searchMemories(query)
    suspend fun getRecentApprovedMemories(): List<MemoryItem> = database.memoryDao().getRecentApprovedMemories()
    suspend fun saveMemory(key: String, content: String, category: String = "GENERAL"): Long {
        return database.memoryDao().insertMemory(
            MemoryItem(key = key, content = content, category = category, userApproved = true)
        )
    }
    suspend fun deleteMemory(id: Long) = database.memoryDao().deleteMemoryById(id)
    suspend fun clearAllMemories() = database.memoryDao().clearAllMemories()

    // Task operations
    val allTasks: Flow<List<JarvisTask>> = database.taskDao().getAllTasks()
    val pendingTasks: Flow<List<JarvisTask>> = database.taskDao().getPendingTasks()
    suspend fun getPendingTasksSync(): List<JarvisTask> = database.taskDao().getPendingTasksSync()
    suspend fun addTask(title: String, description: String = "", priority: String = "MEDIUM", dueDate: Long? = null): Long {
        return database.taskDao().insertTask(
            JarvisTask(title = title, description = description, priority = priority, dueDate = dueDate)
        )
    }
    suspend fun toggleTaskCompleted(id: Long, isCompleted: Boolean) = database.taskDao().setTaskCompleted(id, isCompleted)
    suspend fun deleteTask(id: Long) = database.taskDao().deleteTaskById(id)
    suspend fun clearCompletedTasks() = database.taskDao().clearCompletedTasks()

    // Reminder operations
    val allReminders: Flow<List<JarvisReminder>> = database.reminderDao().getAllReminders()
    suspend fun addReminder(title: String, triggerTimeMillis: Long, repeatDaily: Boolean = false): Long {
        return database.reminderDao().insertReminder(
            JarvisReminder(title = title, triggerTimeMillis = triggerTimeMillis, repeatDaily = repeatDaily)
        )
    }
    suspend fun getDueReminders(currentTime: Long): List<JarvisReminder> = database.reminderDao().getDueReminders(currentTime)
    suspend fun getUpcomingRemindersSync(): List<JarvisReminder> = database.reminderDao().getUpcomingRemindersSync()
    suspend fun markReminderTriggered(id: Long) = database.reminderDao().markAsTriggered(id)
    suspend fun deleteReminder(id: Long) = database.reminderDao().deleteReminderById(id)
    suspend fun clearAllReminders() = database.reminderDao().clearAllReminders()

    // History operations
    val recentHistory: Flow<List<ActionHistoryItem>> = database.historyDao().getRecentHistory()
    suspend fun logAction(command: String, actionType: String, result: String, isSuccess: Boolean = true): Long {
        return database.historyDao().insertHistory(
            ActionHistoryItem(command = command, actionType = actionType, result = result, isSuccess = isSuccess)
        )
    }
    suspend fun clearHistory() = database.historyDao().clearHistory()

    // Automation operations
    val automationRules: Flow<List<AutomationRule>> = database.automationDao().getAllRules()
    suspend fun getActiveRules(): List<AutomationRule> = database.automationDao().getActiveRules()
    suspend fun addAutomationRule(rule: AutomationRule): Long = database.automationDao().insertRule(rule)
    suspend fun deleteAutomationRule(rule: AutomationRule) = database.automationDao().deleteRule(rule)
}
