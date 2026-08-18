package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ScreenSearchDesktop
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.device.CommunicationType
import com.example.ui.JarvisViewModel
import com.example.ui.components.ControlDrawer
import com.example.ui.components.HudTopBar
import com.example.ui.components.HudTranscript
import com.example.ui.components.JarvisOrb
import com.example.ui.components.OrbState
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PureBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: JarvisViewModel,
    onNavigateToAiConfig: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToAutomation: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToActionHistory: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val orbState by viewModel.orbState.collectAsStateWithLifecycle()
    val audioAmplitude by viewModel.audioAmplitude.collectAsStateWithLifecycle()
    val userSpokenText by viewModel.userSpokenText.collectAsStateWithLifecycle()
    val assistantResponseText by viewModel.assistantResponseText.collectAsStateWithLifecycle()
    val statusNotice by viewModel.statusNotice.collectAsStateWithLifecycle()
    val batteryPercentage by viewModel.batteryPercentage.collectAsStateWithLifecycle()
    val isApiKeyConfigured by viewModel.isApiKeyConfigured.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pendingAction by viewModel.pendingConfirmationAction.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState()
    var isDrawerOpen by remember { mutableStateOf(false) }

    // Permission launcher for Record Audio
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        }
    }

    val handleOrbTap = {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            viewModel.onOrbClicked()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            HudTopBar(
                orbState = orbState,
                batteryPercentage = batteryPercentage,
                proactiveMode = settings.proactiveMode,
                focusModeEnabled = settings.focusModeEnabled,
                isApiKeyConfigured = isApiKeyConfigured,
                onOpenSettings = { isDrawerOpen = true }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ObsidianDark,
                            PureBlack
                        ),
                        radius = 1200f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Centered Animated circular J.A.R.V.I.S Orb
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        JarvisOrb(
                            state = orbState,
                            audioAmplitude = audioAmplitude,
                            onClick = handleOrbTap
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Transcript & Spoken Prompt Readout
                        HudTranscript(
                            userSpokenText = userSpokenText,
                            assistantResponseText = assistantResponseText,
                            statusNotice = statusNotice
                        )
                    }
                }

                // Quick Action HUD Directives
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NEURAL COMMAND MATRIX",
                        color = TextMuted,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickCommandChip(label = "Daily Briefing") {
                            viewModel.startListening()
                        }
                        QuickCommandChip(label = "Screen Reader") {
                            viewModel.analyzeCurrentScreen()
                        }
                        QuickCommandChip(label = "Notifications") {
                            viewModel.readRecentNotifications()
                        }
                        QuickCommandChip(label = "Tasks & Alarms") {
                            onNavigateToTasks()
                        }
                        QuickCommandChip(label = if (settings.focusModeEnabled) "End Focus" else "Focus Mode") {
                            viewModel.toggleFocusMode(!settings.focusModeEnabled)
                        }
                    }
                }
            }
        }

        // Safety Confirmation Dialog for Calls / SMS / Sensitive Actions
        if (pendingAction != null) {
            val action = pendingAction!!
            AlertDialog(
                onDismissRequest = { viewModel.cancelPendingAction() },
                containerColor = ObsidianSurface,
                titleContentColor = JarvisCyan,
                textContentColor = TextPrimary,
                icon = {
                    Icon(
                        imageVector = when (action.type) {
                            CommunicationType.PHONE_CALL -> Icons.Default.Phone
                            CommunicationType.SEND_SMS -> Icons.Default.Security
                            else -> Icons.Default.Security
                        },
                        contentDescription = "Confirmation",
                        tint = JarvisAmber,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = when (action.type) {
                            CommunicationType.PHONE_CALL -> "CONFIRM CALL INITIATION"
                            CommunicationType.SEND_SMS -> "CONFIRM MESSAGE COMPOSITION"
                            CommunicationType.DELETE_DATA -> "CONFIRM DATA REMOVAL"
                            CommunicationType.SYSTEM_SETTING -> "CONFIRM SYSTEM ACTION"
                        },
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        color = JarvisAmber
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = action.actionSummary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        if (action.messageBody.isNotBlank()) {
                            Text(
                                text = "Message: \"${action.messageBody}\"",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "J.A.R.V.I.S requires explicit verification prior to hardware execution.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmPendingAction() },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = PureBlack),
                        modifier = Modifier.testTag("confirm_action_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EXECUTE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.cancelPendingAction() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisRed),
                        modifier = Modifier.testTag("cancel_action_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ABORT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
        }

        // Control Matrix Bottom Sheet
        if (isDrawerOpen) {
            ControlDrawer(
                sheetState = sheetState,
                onDismiss = {
                    coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) isDrawerOpen = false
                    }
                },
                onOpenAiConfig = onNavigateToAiConfig,
                onOpenMemory = onNavigateToMemory,
                onOpenTasks = onNavigateToTasks,
                onOpenAutomation = onNavigateToAutomation,
                onOpenVoiceSettings = onNavigateToVoice,
                onOpenHistory = onNavigateToActionHistory,
                onOpenPermissions = onNavigateToPermissions
            )
        }
    }
}

@Composable
private fun QuickCommandChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = JarvisCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

