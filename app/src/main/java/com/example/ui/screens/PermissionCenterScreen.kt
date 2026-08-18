package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.device.access.AccessCategory
import com.example.device.access.SpecialAccessItem
import com.example.device.access.SpecialAccessKey
import com.example.device.access.SpecialAccessStatus
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PureBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

enum class AccessFilter(val label: String) {
    ALL("All Accesses"),
    ATTENTION("⚠️ Needs Attention"),
    SPECIAL_ACCESS("Special Access"),
    SECURITY("Security & Admin"),
    HARDWARE("Hardware Sensors")
}

@Composable
fun PermissionCenterScreen(
    specialAccessItems: List<SpecialAccessItem>,
    onRefreshAccesses: () -> Unit,
    onOpenAccessSettings: (SpecialAccessItem) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedFilter by remember { mutableStateOf(AccessFilter.ALL) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Automatic verification whenever returning to J.A.R.V.I.S from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshAccesses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission request launcher for standard runtime permissions
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onRefreshAccesses()
    }

    val handleAccessClick = { item: SpecialAccessItem ->
        when (item.key) {
            SpecialAccessKey.MICROPHONE -> {
                runtimePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            SpecialAccessKey.POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runtimePermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onOpenAccessSettings(item)
                }
            }
            SpecialAccessKey.CAMERA -> {
                runtimePermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            SpecialAccessKey.CONTACTS -> {
                runtimePermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
            SpecialAccessKey.LOCATION -> {
                runtimePermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            SpecialAccessKey.NEARBY_DEVICES_BLUETOOTH -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runtimePermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
                } else {
                    onOpenAccessSettings(item)
                }
            }
            else -> {
                onOpenAccessSettings(item)
            }
        }
    }

    val filteredItems = remember(specialAccessItems, selectedFilter) {
        when (selectedFilter) {
            AccessFilter.ALL -> specialAccessItems
            AccessFilter.ATTENTION -> specialAccessItems.filter { it.status == SpecialAccessStatus.NOT_GRANTED }
            AccessFilter.SPECIAL_ACCESS -> specialAccessItems.filter { it.category == AccessCategory.SPECIAL_SYSTEM_ACCESS }
            AccessFilter.SECURITY -> specialAccessItems.filter { it.category == AccessCategory.SYSTEM_SECURITY || it.category == AccessCategory.DEVICE_CONNECTIVITY }
            AccessFilter.HARDWARE -> specialAccessItems.filter { it.category == AccessCategory.HARDWARE_SENSORS }
        }
    }

    val grantedCount = specialAccessItems.count { it.status == SpecialAccessStatus.GRANTED }
    val attentionCount = specialAccessItems.count { it.status == SpecialAccessStatus.NOT_GRANTED }
    val notRequiredCount = specialAccessItems.count { it.status == SpecialAccessStatus.NOT_REQUIRED || it.status == SpecialAccessStatus.NOT_AVAILABLE }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("permissions_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back",
                            tint = JarvisCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SPECIAL ACCESS MATRIX",
                            color = JarvisCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Centralized Android System Permissions",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        isRefreshing = true
                        onRefreshAccesses()
                    },
                    modifier = Modifier.testTag("rescan_permissions_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Re-scan System Permissions",
                        tint = JarvisCyan
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Telemetry Overview Card
            item {
                AccessTelemetryHeader(
                    grantedCount = grantedCount,
                    attentionCount = attentionCount,
                    totalCount = specialAccessItems.size,
                    androidVersion = Build.VERSION.RELEASE,
                    sdkInt = Build.VERSION.SDK_INT
                )
            }

            // Filter Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccessFilter.entries.forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) JarvisCyan.copy(alpha = 0.2f) else ObsidianSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) JarvisCyan else ObsidianCardBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = filter.label,
                                color = if (isSelected) JarvisCyan else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Access List Items
            items(filteredItems, key = { it.key.name }) { accessItem ->
                SpecialAccessCard(
                    item = accessItem,
                    onActionClick = { handleAccessClick(accessItem) }
                )
            }

            // Security Architecture Note
            item {
                SecurityArchitectureCard()
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AccessTelemetryHeader(
    grantedCount: Int,
    attentionCount: Int,
    totalCount: Int,
    androidVersion: String,
    sdkInt: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACCESS TELEMETRY STATUS",
                    color = TextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Android $androidVersion (API $sdkInt)",
                    color = JarvisCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusMetricBadge(
                    modifier = Modifier.weight(1f),
                    label = "Granted",
                    count = "$grantedCount",
                    symbol = "✅",
                    color = JarvisCyan
                )
                StatusMetricBadge(
                    modifier = Modifier.weight(1f),
                    label = "Attention",
                    count = "$attentionCount",
                    symbol = "⚠️",
                    color = if (attentionCount > 0) JarvisAmber else TextMuted
                )
                StatusMetricBadge(
                    modifier = Modifier.weight(1f),
                    label = "System Total",
                    count = "$totalCount",
                    symbol = "⚙️",
                    color = JarvisBlue
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Each access is evaluated dynamically against your device OS. J.A.R.V.I.S operates safely in zero-privilege fallback mode if any optional access is declined.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun StatusMetricBadge(
    modifier: Modifier,
    label: String,
    count: String,
    symbol: String,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianSurface)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = symbol, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = count,
                    color = color,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = label,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SpecialAccessCard(
    item: SpecialAccessItem,
    onActionClick: () -> Unit
) {
    val icon = getAccessIcon(item.key)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("access_card_${item.key.name.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianDark),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.status == SpecialAccessStatus.NOT_GRANTED && item.isCriticalForVoice) JarvisCrimson.copy(alpha = 0.6f)
            else if (item.status == SpecialAccessStatus.GRANTED) JarvisCyan.copy(alpha = 0.3f)
            else ObsidianCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title & Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = item.name,
                            tint = when (item.status) {
                                SpecialAccessStatus.GRANTED -> JarvisCyan
                                SpecialAccessStatus.NOT_GRANTED -> JarvisAmber
                                SpecialAccessStatus.NOT_AVAILABLE -> TextMuted
                                SpecialAccessStatus.NOT_REQUIRED -> JarvisBlue
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = item.name,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = item.category.displayName,
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                StatusBadge(status = item.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Purpose & Feature Context
            Text(
                text = item.purpose,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Context tag & detail
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feature: ${item.featureContext}",
                    color = JarvisCyan.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.statusDetail,
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.targetSettingsScreen,
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )

                when (item.status) {
                    SpecialAccessStatus.GRANTED -> {
                        OutlinedButton(
                            onClick = onActionClick,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            modifier = Modifier.testTag("manage_btn_${item.key.name.lowercase()}")
                        ) {
                            Text("Manage in Settings", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp))
                        }
                    }
                    SpecialAccessStatus.NOT_GRANTED -> {
                        Button(
                            onClick = onActionClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.isCriticalForVoice) JarvisCrimson else JarvisCyan,
                                contentColor = PureBlack
                            ),
                            modifier = Modifier.testTag("enable_btn_${item.key.name.lowercase()}")
                        ) {
                            Text(
                                text = if (item.isCriticalForVoice) "Grant Required" else "Enable in Settings",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp))
                        }
                    }
                    SpecialAccessStatus.NOT_REQUIRED -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ObsidianSurface)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Managed by OS",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    SpecialAccessStatus.NOT_AVAILABLE -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ObsidianSurface)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Not Available",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: SpecialAccessStatus) {
    val (bgColor, textColor, label) = when (status) {
        SpecialAccessStatus.GRANTED -> Triple(JarvisCyan.copy(alpha = 0.15f), JarvisCyan, "✅ Granted")
        SpecialAccessStatus.NOT_GRANTED -> Triple(JarvisAmber.copy(alpha = 0.15f), JarvisAmber, "⚠️ Not Granted")
        SpecialAccessStatus.NOT_AVAILABLE -> Triple(ObsidianSurface, TextMuted, "❌ Not Available")
        SpecialAccessStatus.NOT_REQUIRED -> Triple(JarvisBlue.copy(alpha = 0.15f), JarvisBlue, "ℹ️ Not Required")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SecurityArchitectureCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = JarvisCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "J.A.R.V.I.S SECURITY ARCHITECTURE",
                    color = JarvisCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Zero Silent Grants: J.A.R.V.I.S never exploits background services or attempts silent permission bypasses.\n" +
                       "• Complete Isolation: Denying any special access will only disable that specific capability while keeping the core assistant running.\n" +
                       "• Local Hardware Security: API keys and memories remain secured in the Android Keystore.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

private fun getAccessIcon(key: SpecialAccessKey): ImageVector {
    return when (key) {
        SpecialAccessKey.ACCESSIBILITY_SERVICE -> Icons.Default.Visibility
        SpecialAccessKey.DISPLAY_OVERLAY -> Icons.Default.Widgets
        SpecialAccessKey.NOTIFICATION_LISTENER -> Icons.Default.Notifications
        SpecialAccessKey.USAGE_ACCESS -> Icons.Default.Tune
        SpecialAccessKey.MODIFY_SYSTEM_SETTINGS -> Icons.Default.Tune
        SpecialAccessKey.ALL_FILES_ACCESS -> Icons.Default.Folder
        SpecialAccessKey.DEVICE_ADMIN -> Icons.Default.Lock
        SpecialAccessKey.BATTERY_OPTIMIZATION -> Icons.Default.BatteryStd
        SpecialAccessKey.EXACT_ALARMS -> Icons.Default.Alarm
        SpecialAccessKey.FULL_SCREEN_INTENTS -> Icons.Default.NotificationsPaused
        SpecialAccessKey.INSTALL_UNKNOWN_APPS -> Icons.Default.Download
        SpecialAccessKey.VPN_SERVICE -> Icons.Default.VpnKey
        SpecialAccessKey.COMPANION_DEVICE -> Icons.Default.Bluetooth
        SpecialAccessKey.NEARBY_DEVICES_BLUETOOTH -> Icons.Default.Bluetooth
        SpecialAccessKey.DND_POLICY_ACCESS -> Icons.Default.NotificationsPaused
        SpecialAccessKey.MICROPHONE -> Icons.Default.Mic
        SpecialAccessKey.POST_NOTIFICATIONS -> Icons.Default.Notifications
        SpecialAccessKey.CAMERA -> Icons.Default.CameraAlt
        SpecialAccessKey.CONTACTS -> Icons.Default.Contacts
        SpecialAccessKey.LOCATION -> Icons.Default.LocationOn
    }
}
