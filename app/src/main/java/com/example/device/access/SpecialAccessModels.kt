package com.example.device.access

import androidx.compose.ui.graphics.vector.ImageVector

enum class SpecialAccessStatus(
    val title: String,
    val symbol: String
) {
    GRANTED("Granted", "✅"),
    NOT_GRANTED("Not Granted", "⚠️"),
    NOT_AVAILABLE("Not Available", "❌"),
    NOT_REQUIRED("Not Required", "ℹ️")
}

enum class AccessCategory(val displayName: String) {
    SPECIAL_SYSTEM_ACCESS("Special System Access"),
    SYSTEM_SECURITY("Security & Administration"),
    DEVICE_CONNECTIVITY("Connectivity & Peripherals"),
    HARDWARE_SENSORS("Hardware & Telemetry Sensors")
}

enum class SpecialAccessKey {
    ACCESSIBILITY_SERVICE,
    DISPLAY_OVERLAY,
    NOTIFICATION_LISTENER,
    USAGE_ACCESS,
    MODIFY_SYSTEM_SETTINGS,
    ALL_FILES_ACCESS,
    DEVICE_ADMIN,
    BATTERY_OPTIMIZATION,
    EXACT_ALARMS,
    FULL_SCREEN_INTENTS,
    INSTALL_UNKNOWN_APPS,
    VPN_SERVICE,
    COMPANION_DEVICE,
    NEARBY_DEVICES_BLUETOOTH,
    DND_POLICY_ACCESS,
    
    // Hardware & Sensor permissions
    MICROPHONE,
    POST_NOTIFICATIONS,
    CAMERA,
    CONTACTS,
    LOCATION
}

data class SpecialAccessItem(
    val key: SpecialAccessKey,
    val name: String,
    val purpose: String,
    val featureContext: String,
    val category: AccessCategory,
    val status: SpecialAccessStatus,
    val statusDetail: String,
    val isCriticalForVoice: Boolean = false,
    val minApiLevel: Int = 1,
    val targetSettingsScreen: String
)
