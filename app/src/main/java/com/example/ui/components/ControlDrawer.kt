package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlDrawer(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onOpenAiConfig: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianDark,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(JarvisCyan.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "J.A.R.V.I.S CONTROL MATRIX",
                color = JarvisCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(16.dp))

            DrawerMenuItem(
                title = "AI Configuration",
                subtitle = "Gemini Core API Key & Vault Link",
                icon = Icons.Default.Key,
                tag = "drawer_ai_config",
                onClick = {
                    onDismiss()
                    onOpenAiConfig()
                }
            )

            DrawerMenuItem(
                title = "Memory Matrix",
                subtitle = "User Knowledge, Facts & Long-term Context",
                icon = Icons.Default.Psychology,
                tag = "drawer_memory",
                onClick = {
                    onDismiss()
                    onOpenMemory()
                }
            )

            DrawerMenuItem(
                title = "Directives & Alarms",
                subtitle = "Task Scheduling, Priority & Timers",
                icon = Icons.Default.Alarm,
                tag = "drawer_tasks",
                onClick = {
                    onDismiss()
                    onOpenTasks()
                }
            )

            DrawerMenuItem(
                title = "Protocols & Smart Silence",
                subtitle = "Focus Mode, Study Mode, Proactive Triggers",
                icon = Icons.Default.SettingsSuggest,
                tag = "drawer_automation",
                onClick = {
                    onDismiss()
                    onOpenAutomation()
                }
            )

            DrawerMenuItem(
                title = "Voice Synthesizer",
                subtitle = "Natural Male TTS, Cadence & Language",
                icon = Icons.Default.RecordVoiceOver,
                tag = "drawer_voice",
                onClick = {
                    onDismiss()
                    onOpenVoiceSettings()
                }
            )

            DrawerMenuItem(
                title = "Action Telemetry Logs",
                subtitle = "Command Execution & Route History",
                icon = Icons.Default.History,
                tag = "drawer_history",
                onClick = {
                    onDismiss()
                    onOpenHistory()
                }
            )

            DrawerMenuItem(
                title = "Permissions & Special Access",
                subtitle = "System Access, Accessibility, Overlays & Security",
                icon = Icons.Default.Shield,
                tag = "drawer_permissions",
                onClick = {
                    onDismiss()
                    onOpenPermissions()
                }
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = JarvisCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
