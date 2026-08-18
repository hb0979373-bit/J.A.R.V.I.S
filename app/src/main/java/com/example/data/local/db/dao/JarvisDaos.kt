package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.db.entity.ActionHistoryItem
import com.example.data.local.db.entity.AutomationRule
import com.example.data.local.db.entity.JarvisReminder
import com.example.data.local.db.entity.JarvisTask
import com.example.data.local.db.entity.MemoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_items WHERE userApproved = 1 ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memory_items WHERE userApproved = 1 AND (key LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchMemories(query: String): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memory_items WHERE userApproved = 1 LIMIT 20")
    suspend fun getRecentApprovedMemories(): List<MemoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(item: MemoryItem): Long

    @Delete
    suspend fun deleteMemory(item: MemoryItem)

    @Query("DELETE FROM memory_items WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memory_items")
    suspend fun clearAllMemories()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, timestamp DESC")
    fun getAllTasks(): Flow<List<JarvisTask>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY timestamp DESC")
    fun getPendingTasks(): Flow<List<JarvisTask>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0")
    suspend fun getPendingTasksSync(): List<JarvisTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: JarvisTask): Long

    @Update
    suspend fun updateTask(task: JarvisTask)

    @Query("UPDATE tasks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, isCompleted: Boolean)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM tasks WHERE isCompleted = 1")
    suspend fun clearCompletedTasks()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY triggerTimeMillis ASC")
    fun getAllReminders(): Flow<List<JarvisReminder>>

    @Query("SELECT * FROM reminders WHERE isTriggered = 0 AND triggerTimeMillis <= :currentTime")
    suspend fun getDueReminders(currentTime: Long): List<JarvisReminder>

    @Query("SELECT * FROM reminders WHERE isTriggered = 0 ORDER BY triggerTimeMillis ASC")
    suspend fun getUpcomingRemindersSync(): List<JarvisReminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: JarvisReminder): Long

    @Update
    suspend fun updateReminder(reminder: JarvisReminder)

    @Query("UPDATE reminders SET isTriggered = 1 WHERE id = :id")
    suspend fun markAsTriggered(id: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun clearAllReminders()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM action_history ORDER BY timestamp DESC LIMIT 100")
    fun getRecentHistory(): Flow<List<ActionHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: ActionHistoryItem): Long

    @Query("DELETE FROM action_history")
    suspend fun clearHistory()
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automation_rules ORDER BY id ASC")
    fun getAllRules(): Flow<List<AutomationRule>>

    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    suspend fun getActiveRules(): List<AutomationRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutomationRule): Long

    @Update
    suspend fun updateRule(rule: AutomationRule)

    @Delete
    suspend fun deleteRule(rule: AutomationRule)
}
