package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.JarvisViewModel
import com.example.ui.screens.ActionHistoryScreen
import com.example.ui.screens.AiConfigScreen
import com.example.ui.screens.AutomationModesScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.MemoryCenterScreen
import com.example.ui.screens.PermissionCenterScreen
import com.example.ui.screens.TasksRemindersScreen
import com.example.ui.screens.VoiceSettingsScreen
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.PureBlack

object JarvisRoutes {
    const val MAIN = "main"
    const val AI_CONFIG = "ai_config"
    const val MEMORY = "memory"
    const val TASKS = "tasks"
    const val AUTOMATION = "automation"
    const val VOICE = "voice"
    const val HISTORY = "history"
    const val PERMISSIONS = "permissions"
}

class MainActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PureBlack
                ) {
                    JarvisAppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun JarvisAppNavigation(viewModel: JarvisViewModel) {
    val navController = rememberNavController()

    val currentMaskedKey by viewModel.currentMaskedApiKey.collectAsStateWithLifecycle()
    val isKeyConfigured by viewModel.isApiKeyConfigured.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val history by viewModel.actionHistory.collectAsStateWithLifecycle()
    val specialAccessItems by viewModel.specialAccessList.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = JarvisRoutes.MAIN
    ) {
        composable(JarvisRoutes.MAIN) {
            MainScreen(
                viewModel = viewModel,
                onNavigateToAiConfig = { navController.navigate(JarvisRoutes.AI_CONFIG) },
                onNavigateToMemory = { navController.navigate(JarvisRoutes.MEMORY) },
                onNavigateToTasks = { navController.navigate(JarvisRoutes.TASKS) },
                onNavigateToAutomation = { navController.navigate(JarvisRoutes.AUTOMATION) },
                onNavigateToVoice = { navController.navigate(JarvisRoutes.VOICE) },
                onNavigateToActionHistory = { navController.navigate(JarvisRoutes.HISTORY) },
                onNavigateToPermissions = { navController.navigate(JarvisRoutes.PERMISSIONS) }
            )
        }

        composable(JarvisRoutes.AI_CONFIG) {
            AiConfigScreen(
                currentMaskedKey = currentMaskedKey,
                isKeyConfigured = isKeyConfigured,
                connectionStatus = connectionStatus,
                onSaveApiKey = { key -> viewModel.saveApiKey(key) },
                onTestConnection = { viewModel.testAiConnection() },
                onRemoveApiKey = { viewModel.removeApiKey() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(JarvisRoutes.MEMORY) {
            MemoryCenterScreen(
                memories = memories,
                onSaveMemory = { key, content -> viewModel.saveMemory(key, content) },
                onDeleteMemory = { id -> viewModel.deleteMemory(id) },
                onClearAllMemories = { viewModel.clearAllMemories() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(JarvisRoutes.TASKS) {
            TasksRemindersScreen(
                tasks = tasks,
                reminders = reminders,
                onAddTask = { title, priority -> viewModel.addTask(title, priority) },
                onToggleTaskCompleted = { id, comp -> viewModel.toggleTaskCompleted(id, comp) },
                onDeleteTask = { id -> viewModel.deleteTask(id) },
                onAddReminder = { title, mins -> viewModel.addReminder(title, mins) },
                onDeleteReminder = { id -> viewModel.deleteReminder(id) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(JarvisRoutes.AUTOMATION) {
            AutomationModesScreen(
                settings = settings,
                onToggleFocusMode = { enabled -> viewModel.toggleFocusMode(enabled) },
                onToggleStudyMode = { enabled -> viewModel.toggleStudyMode(enabled) },
                onUpdateProactiveMode = { mode -> viewModel.updateProactiveMode(mode) },
                onUpdateCooldown = { mins -> viewModel.updateProactiveCooldown(mins) },
                onUpdateQuietHours = { enabled, start, end -> viewModel.updateQuietHours(enabled, start, end) },
                onUpdateResponseMode = { mode -> viewModel.updateResponseMode(mode) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(JarvisRoutes.VOICE) {
            VoiceSettingsScreen(
                settings = settings,
                onUpdateSpeechSettings = { speed, pitch, lang -> viewModel.updateSpeechSettings(speed, pitch, lang) },
                onUpdateAssistantName = { name -> viewModel.updateAssistantName(name) },
                onToggleContinuousListening = { enabled -> viewModel.setContinuousListening(enabled) },
                onTestVoiceSample = { text -> viewModel.testVoiceSample(text) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(JarvisRoutes.HISTORY) {
            ActionHistoryScreen(
                history = history,
                onClearHistory = { viewModel.clearActionHistory() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(JarvisRoutes.PERMISSIONS) {
            PermissionCenterScreen(
                specialAccessItems = specialAccessItems,
                onRefreshAccesses = { viewModel.refreshSpecialAccesses() },
                onOpenAccessSettings = { item -> viewModel.openSpecialAccess(item) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
