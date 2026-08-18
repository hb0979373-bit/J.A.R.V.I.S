package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.db.dao.AutomationDao
import com.example.data.local.db.dao.HistoryDao
import com.example.data.local.db.dao.MemoryDao
import com.example.data.local.db.dao.ReminderDao
import com.example.data.local.db.dao.TaskDao
import com.example.data.local.db.entity.ActionHistoryItem
import com.example.data.local.db.entity.AutomationRule
import com.example.data.local.db.entity.JarvisReminder
import com.example.data.local.db.entity.JarvisTask
import com.example.data.local.db.entity.MemoryItem

@Database(
    entities = [
        MemoryItem::class,
        JarvisTask::class,
        JarvisReminder::class,
        ActionHistoryItem::class,
        AutomationRule::class
    ],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun historyDao(): HistoryDao
    abstract fun automationDao(): AutomationDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_core.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
