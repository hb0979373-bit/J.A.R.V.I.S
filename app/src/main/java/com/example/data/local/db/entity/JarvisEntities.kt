package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_items")
data class MemoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val content: String,
    val category: String = "GENERAL", // GENERAL, PREFERENCE, PERSONAL, FACT, WORK
    val timestamp: Long = System.currentTimeMillis(),
    val userApproved: Boolean = true
)

@Entity(tableName = "tasks")
data class JarvisTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH, URGENT
    val isCompleted: Boolean = false,
    val dueDate: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class JarvisReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerTimeMillis: Long,
    val isTriggered: Boolean = false,
    val repeatDaily: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "action_history")
data class ActionHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val actionType: String, // LOCAL_COMMAND, AI_REASONING, DEVICE_CONTROL, SYSTEM_CHECK
    val result: String,
    val isSuccess: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerType: String, // BATTERY_LOW, TIME_OF_DAY, FOCUS_CHANGE
    val triggerCondition: String, // "<20", "22:00", etc.
    val actionCommand: String, // "announce_battery", "enter_quiet", etc.
    val isEnabled: Boolean = true
)
