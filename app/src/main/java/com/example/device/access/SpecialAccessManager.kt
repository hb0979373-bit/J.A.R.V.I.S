package com.example.device.access

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.services.JarvisAccessibilityService
import com.example.services.JarvisDeviceAdminReceiver
import com.example.services.JarvisNotificationListenerService

class SpecialAccessManager(private val context: Context) {

    /**
     * Evaluates all special access and hardware permissions dynamically based on
     * the host device, Android OS level, and active J.A.R.V.I.S capabilities.
     */
    fun evaluateAllAccesses(): List<SpecialAccessItem> {
        val list = mutableListOf<SpecialAccessItem>()

        // 1. Accessibility Service
        list.add(evaluateAccessibilityService())

        // 2. Display Over Other Apps / System Alert Window
        list.add(evaluateDisplayOverlay())

        // 3. Notification Access / Listener
        list.add(evaluateNotificationListener())

        // 4. Usage Access (App Usage Stats)
        list.add(evaluateUsageAccess())

        // 5. Modify System Settings (Write Settings)
        list.add(evaluateModifySystemSettings())

        // 6. All Files Access (Manage External Storage)
        list.add(evaluateAllFilesAccess())

        // 7. Device Administrator
        list.add(evaluateDeviceAdmin())

        // 8. Battery Optimization Exemption
        list.add(evaluateBatteryOptimization())

        // 9. Exact Alarm Access
        list.add(evaluateExactAlarms())

        // 10. Full-Screen Intent Permission
        list.add(evaluateFullScreenIntent())

        // 11. Install Unknown Apps
        list.add(evaluateInstallUnknownApps())

        // 12. VPN Service Access
        list.add(evaluateVpnService())

        // 13. Do Not Disturb / Notification Policy Access
        list.add(evaluateDndPolicyAccess())

        // 14. Companion Device / Connected Device
        list.add(evaluateCompanionDevice())

        // 15. Nearby Devices / Bluetooth
        list.add(evaluateNearbyDevices())

        // 16. Microphone (Critical for Voice)
        list.add(evaluateMicrophone())

        // 17. Notification Delivery
        list.add(evaluatePostNotifications())

        // 18. Camera Visual Telemetry
        list.add(evaluateCamera())

        // 19. Contacts Intelligence
        list.add(evaluateContacts())

        // 20. Precise Location
        list.add(evaluateLocation())

        return list
    }

    // --- EVALUATION LOGIC FOR EACH SPECIAL ACCESS ---

    private fun evaluateAccessibilityService(): SpecialAccessItem {
        val isGranted = try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val expectedComponent = ComponentName(context, JarvisAccessibilityService::class.java).flattenToString()
            val expectedShortComponent = ComponentName(context, JarvisAccessibilityService::class.java).flattenToShortString()
            
            enabledServices.contains(expectedComponent) || enabledServices.contains(expectedShortComponent) || JarvisAccessibilityService.isRunning
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service status", e)
            false
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.ACCESSIBILITY_SERVICE,
            name = "Accessibility Service",
            purpose = "Allows J.A.R.V.I.S to detect active on-screen content, automate navigation workflows, and interact with assistive app directives.",
            featureContext = "Screen Reading & Voice Automation",
            category = AccessCategory.SPECIAL_SYSTEM_ACCESS,
            status = if (isGranted) SpecialAccessStatus.GRANTED else SpecialAccessStatus.NOT_GRANTED,
            statusDetail = if (isGranted) "Neural Screen Bridge Active" else "Requires manual enablement in Accessibility settings",
            targetSettingsScreen = "Accessibility Settings"
        )
    }

    private fun evaluateDisplayOverlay(): SpecialAccessItem {
        val (status, detail) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val canDraw = Settings.canDrawOverlays(context)
            if (canDraw) SpecialAccessStatus.GRANTED to "Floating Orb Overlay enabled"
            else SpecialAccessStatus.NOT_GRANTED to "Overlay permission required for HUD floating orb"
        } else {
            SpecialAccessStatus.NOT_REQUIRED to "Automatically granted on this Android OS version"
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.DISPLAY_OVERLAY,
            name = "Display Over Other Apps",
            purpose = "Permits J.A.R.V.I.S to display the floating holographic HUD orb, transcript readout, and voice controls over other apps.",
            featureContext = "Floating Holographic Orb HUD",
            category = AccessCategory.SPECIAL_SYSTEM_ACCESS,
            status = status,
            statusDetail = detail,
            targetSettingsScreen = "Special App Access → Display Over Apps"
        )
    }

    private fun evaluateNotificationListener(): SpecialAccessItem {
        val isGranted = try {
            val listeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            listeners.contains(context.packageName) || JarvisNotificationListenerService.isConnected
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification listener status", e)
            false
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.NOTIFICATION_LISTENER,
            name = "Notification Access",
            purpose = "Enables J.A.R.V.I.S to intercept, summarize, and prioritize notifications aloud, and suppress distractions during Focus Mode.",
            featureContext = "Notification Summaries & Focus Mode Filter",
            category = AccessCategory.SPECIAL_SYSTEM_ACCESS,
            status = if (isGranted) SpecialAccessStatus.GRANTED else SpecialAccessStatus.NOT_GRANTED,
            statusDetail = if (isGranted) "Notification Interceptor active" else "Requires toggle in Device & App Notification settings",
            targetSettingsScreen = "Special App Access → Notification Access"
        )
    }

    private fun evaluateUsageAccess(): SpecialAccessItem {
        val isGranted = try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            if (appOps != null) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName
                    )
                }
                mode == AppOpsManager.MODE_ALLOWED
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking usage access", e)
            false
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.USAGE_ACCESS,
            name = "Usage Access",
            purpose = "Allows J.A.R.V.I.S to monitor device usage patterns, track screen focus duration, and automate Study Mode suggestions.",
            featureContext = "Focus Telemetry & Proactive Study Mode",
            category = AccessCategory.SPECIAL_SYSTEM_ACCESS,
            status = if (isGranted) SpecialAccessStatus.GRANTED else SpecialAccessStatus.NOT_GRANTED,
            statusDetail = if (isGranted) "App Usage Telemetry enabled" else "Requires toggle in Usage Access settings",
            targetSettingsScreen = "Special App Access → Usage Access"
        )
    }

    private fun evaluateModifySystemSettings(): SpecialAccessItem {
        val (status, detail) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val canWrite = Settings.System.canWrite(context)
            if (canWrite) SpecialAccessStatus.GRANTED to "System Settings modification permitted"
            else SpecialAccessStatus.NOT_GRANTED to "Required to change screen brightness and system audio directly"
        } else {
            SpecialAccessStatus.NOT_REQUIRED to "Granted on older Android OS"
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.MODIFY_SYSTEM_SETTINGS,
            name = "Modify System Settings",
            purpose = "Permits J.A.R.V.I.S to adjust screen brightness, display timeout, and hardware volume profiles on voice command.",
            featureContext = "Direct Hardware & Audio Calibration",
            category = AccessCategory.SPECIAL_SYSTEM_ACCESS,
            status = status,
            statusDetail = detail,
            targetSettingsScreen = "Special App Access → Modify System Settings"
        )
    }

    private fun evaluateAllFilesAccess(): SpecialAccessItem {
        val (status, detail) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isManager = Environment.isExternalStorageManager()
            if (isManager) SpecialAccessStatus.GRANTED to "Broad Storage & Document indexing enabled"
            else SpecialAccessStatus.NOT_GRANTED to "Required to index local documents, logs, and backups"
        } else {
            SpecialAccessStatus.NOT_REQUIRED to "Standard storage permissions apply on this Android version"
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.ALL_FILES_ACCESS,
            name = "All Files Access",
            purpose = "Allows J.A.R.V.I.S to index user files, locate requested documents, and manage backup vaults on voice request.",
            featureContext = "Document Indexing & Memory Vault",
            category = AccessCategory.SPECIAL_SYSTEM_ACCESS,
            status = status,
            statusDetail = detail,
            minApiLevel = Build.VERSION_CODES.R,
            targetSettingsScreen = "Special App Access → All Files Access"
        )
    }

    private fun evaluateDeviceAdmin(): SpecialAccessItem {
        val isGranted = try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, JarvisDeviceAdminReceiver::class.java)
            dpm?.isAdminActive(adminComponent) == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device admin", e)
            false
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.DEVICE_ADMIN,
            name = "Device Administrator",
            purpose = "Permits J.A.R.V.I.S to execute instant device lock protocols and screen shutdown directives on emergency voice command.",
            featureContext = "Security Protocols & Emergency Screen Lock",
            category = AccessCategory.SYSTEM_SECURITY,
            status = if (isGranted) SpecialAccessStatus.GRANTED else SpecialAccessStatus.NOT_GRANTED,
            statusDetail = if (isGranted) "Security Admin Active" else "Not activated. Security lock voice commands will require PIN/Power button.",
            targetSettingsScreen = "Device Administrator Apps"
        )
    }

    private fun evaluateBatteryOptimization(): SpecialAccessItem {
        val (status, detail) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoring = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            if (isIgnoring) SpecialAccessStatus.GRANTED to "Exempt from system standby throttle"
            else SpecialAccessStatus.NOT_GRANTED to "May be delayed by OS Doze mode if not exempted"
        } else {
            SpecialAccessStatus.NOT_REQUIRED to "Not applicable on this Android version"
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.BATTERY_OPTIMIZATION,
            name = "Battery Optimization Exemption",
            purpose = "Prevents Android OS Doze mode from putting J.A.R.V.I.S proactive timers and voice listening standbys to sleep.",
            featureContext = "Reliable Background Standby & Proactive Pulse",
            category = AccessCategory.SPECIAL_SYSTEM_ACCESS,
            status = status,
            statusDetail = detail,
            targetSettingsScreen = "Special App Access → Energy / Battery Optimization"
        )
    }

    private fun evaluateExactAlarms(): SpecialAccessItem {
        val (status, detail) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val canSchedule = alarmManager?.canScheduleExactAlarms() == true
            if (canSchedule) SpecialAccessStatus.GRANTED to "Second-precision alarm scheduling active"
            else SpecialAccessStatus.NOT_GRANTED to "Exact alarms restricted; reminders may be batched by OS"
        } else {
            SpecialAccessStatus.NOT_REQUIRED to "Exact alarms allowed by default on this OS"
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.EXACT_ALARMS,
            name = "Exact Alarm Access",
            purpose = "Guarantees zero-delay, second-precision execution for scheduled task reminders and morning briefing triggers.",
            featureContext = "Precision Alarms & Timed Briefings",
            category = AccessCategory.SPECIAL_SYSTEM_ACCESS,
            status = status,
            statusDetail = detail,
            minApiLevel = Build.VERSION_CODES.S,
            targetSettingsScreen = "Special App Access → Alarms & Reminders"
        )
    }

    private fun evaluateFullScreenIntent(): SpecialAccessItem {
        val (status, detail) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val canUse = nm?.canUseFullScreenIntent() == true
            if (canUse) SpecialAccessStatus.GRANTED to "Full-screen alert capability active"
            else SpecialAccessStatus.NOT_GRANTED to "Full-screen alerts restricted by Android 14+ policy"
        } else {
            SpecialAccessStatus.NOT_REQUIRED to "Full-screen intents enabled by manifest on this OS"
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.FULL_SCREEN_INTENTS,
            name = "Full-Screen Intent Permission",
            purpose = "Enables J.A.R.V.I.S to pop up full-screen interactive emergency alarms and incoming voice dialogs when device is locked.",
            featureContext = "Lock-Screen Emergency Alarms & HUD",
            category = AccessCategory.SPECIAL_SYSTEM_ACCESS,
            status = status,
            statusDetail = detail,
            minApiLevel = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            targetSettingsScreen = "Special App Access → Full-Screen Intent"
        )
    }

    private fun evaluateInstallUnknownApps(): SpecialAccessItem {
        val (status, detail) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = context.packageManager.canRequestPackageInstalls()
            if (canInstall) SpecialAccessStatus.GRANTED to "Package installation assistance permitted"
            else SpecialAccessStatus.NOT_GRANTED to "Disabled. Required only for installing custom modular skills"
        } else {
            SpecialAccessStatus.NOT_REQUIRED to "Managed via global system security settings"
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.INSTALL_UNKNOWN_APPS,
            name = "Install Unknown Apps",
            purpose = "Allows J.A.R.V.I.S to assist in downloading and launching approved modular update APKs on demand.",
            featureContext = "Modular Skill Package Deployment",
            category = AccessCategory.SYSTEM_SECURITY,
            status = status,
            statusDetail = detail,
            minApiLevel = Build.VERSION_CODES.O,
            targetSettingsScreen = "Special App Access → Install Unknown Apps"
        )
    }

    private fun evaluateVpnService(): SpecialAccessItem {
        val isPrepared = try {
            VpnService.prepare(context) == null
        } catch (e: Exception) {
            false
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.VPN_SERVICE,
            name = "VPN & Network Sentinel",
            purpose = "Enables J.A.R.V.I.S local on-device network inspection to shield queries and block telemetry trackers.",
            featureContext = "Privacy Sentinel & Local DNS Shield",
            category = AccessCategory.SYSTEM_SECURITY,
            status = if (isPrepared) SpecialAccessStatus.GRANTED else SpecialAccessStatus.NOT_GRANTED,
            statusDetail = if (isPrepared) "Local VPN profile configured" else "Requires user tap to create VPN connection profile",
            targetSettingsScreen = "Android VPN Connection Dialog"
        )
    }

    private fun evaluateDndPolicyAccess(): SpecialAccessItem {
        val (status, detail) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val isGranted = nm?.isNotificationPolicyAccessGranted == true
            if (isGranted) SpecialAccessStatus.GRANTED to "Do Not Disturb control active"
            else SpecialAccessStatus.NOT_GRANTED to "Required to mute ringer and enable Zen Mode during Focus sessions"
        } else {
            SpecialAccessStatus.NOT_REQUIRED to "Not applicable on this Android version"
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.DND_POLICY_ACCESS,
            name = "Do Not Disturb / Zen Mode Access",
            purpose = "Permits J.A.R.V.I.S to automatically switch device sound profiles to Total Silence or Priority Only during Focus Mode.",
            featureContext = "Autonomous Focus Mode Sound Control",
            category = AccessCategory.SPECIAL_SYSTEM_ACCESS,
            status = status,
            statusDetail = detail,
            targetSettingsScreen = "Special App Access → Do Not Disturb Access"
        )
    }

    private fun evaluateCompanionDevice(): SpecialAccessItem {
        val (status, detail) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val cdm = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
            if (cdm != null) {
                SpecialAccessStatus.NOT_GRANTED to "Available for smart wearables & peripheral pairing"
            } else {
                SpecialAccessStatus.NOT_AVAILABLE to "Companion Device Manager not supported on device"
            }
        } else {
            SpecialAccessStatus.NOT_AVAILABLE to "Requires Android 8.0+ (API 26)"
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.COMPANION_DEVICE,
            name = "Companion Device Access",
            purpose = "Permits J.A.R.V.I.S to maintain continuous low-latency links with smart watches, wireless ear wear, and IoT controllers.",
            featureContext = "Wearable & Smart Accessory Link",
            category = AccessCategory.DEVICE_CONNECTIVITY,
            status = status,
            statusDetail = detail,
            minApiLevel = Build.VERSION_CODES.O,
            targetSettingsScreen = "Connected Devices & Companion Manager"
        )
    }

    private fun evaluateNearbyDevices(): SpecialAccessItem {
        val (status, detail) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (scanGranted && connectGranted) {
                SpecialAccessStatus.GRANTED to "Nearby BLE and audio beacons accessible"
            } else {
                SpecialAccessStatus.NOT_GRANTED to "Nearby Devices runtime permissions required"
            }
        } else {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            if (bluetoothManager?.adapter != null) {
                SpecialAccessStatus.GRANTED to "Legacy Bluetooth radio accessible"
            } else {
                SpecialAccessStatus.NOT_AVAILABLE to "Bluetooth hardware unavailable"
            }
        }

        return SpecialAccessItem(
            key = SpecialAccessKey.NEARBY_DEVICES_BLUETOOTH,
            name = "Nearby Devices & Bluetooth",
            purpose = "Allows J.A.R.V.I.S to detect, pair with, and transmit voice telemetry to nearby Bluetooth speakers and BLE beacons.",
            featureContext = "Bluetooth Audio & IoT Proximity Hub",
            category = AccessCategory.DEVICE_CONNECTIVITY,
            status = status,
            statusDetail = detail,
            targetSettingsScreen = "App Permissions → Nearby Devices"
        )
    }

    private fun evaluateMicrophone(): SpecialAccessItem {
        val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return SpecialAccessItem(
            key = SpecialAccessKey.MICROPHONE,
            name = "Microphone Access",
            purpose = "Required for real-time speech recognition, continuous voice commands, and audio amplitude HUD visualization.",
            featureContext = "Voice Recognition & Neural Orb",
            category = AccessCategory.HARDWARE_SENSORS,
            status = if (isGranted) SpecialAccessStatus.GRANTED else SpecialAccessStatus.NOT_GRANTED,
            statusDetail = if (isGranted) "Audio input streaming enabled" else "Required to speak with J.A.R.V.I.S",
            isCriticalForVoice = true,
            targetSettingsScreen = "App Permissions → Microphone"
        )
    }

    private fun evaluatePostNotifications(): SpecialAccessItem {
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        return SpecialAccessItem(
            key = SpecialAccessKey.POST_NOTIFICATIONS,
            name = "Notification Delivery",
            purpose = "Enables J.A.R.V.I.S to deliver high-priority task reminders, morning intelligence briefings, and system telemetry alerts.",
            featureContext = "Proactive Briefings & Directives",
            category = AccessCategory.HARDWARE_SENSORS,
            status = if (isGranted) SpecialAccessStatus.GRANTED else SpecialAccessStatus.NOT_GRANTED,
            statusDetail = if (isGranted) "Notification channels active" else "Notifications disabled in system settings",
            targetSettingsScreen = "App Notifications Settings"
        )
    }

    private fun evaluateCamera(): SpecialAccessItem {
        val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        return SpecialAccessItem(
            key = SpecialAccessKey.CAMERA,
            name = "Camera Telemetry Access",
            purpose = "Allows J.A.R.V.I.S to analyze real-world objects, inspect documents, and read barcodes on voice directive.",
            featureContext = "Visual Intelligence & Document Scanner",
            category = AccessCategory.HARDWARE_SENSORS,
            status = if (isGranted) SpecialAccessStatus.GRANTED else SpecialAccessStatus.NOT_GRANTED,
            statusDetail = if (isGranted) "Visual sensor active" else "Camera sensor disabled",
            targetSettingsScreen = "App Permissions → Camera"
        )
    }

    private fun evaluateContacts(): SpecialAccessItem {
        val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        return SpecialAccessItem(
            key = SpecialAccessKey.CONTACTS,
            name = "Contacts Intelligence",
            purpose = "Permits J.A.R.V.I.S to resolve contact names when you speak commands like 'Call Jarvis' or 'Message Sarah'.",
            featureContext = "Voice Contact Matching",
            category = AccessCategory.HARDWARE_SENSORS,
            status = if (isGranted) SpecialAccessStatus.GRANTED else SpecialAccessStatus.NOT_GRANTED,
            statusDetail = if (isGranted) "Contacts book accessible" else "Contacts permission not granted",
            targetSettingsScreen = "App Permissions → Contacts"
        )
    }

    private fun evaluateLocation(): SpecialAccessItem {
        val isFineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val isCoarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val isGranted = isFineGranted || isCoarseGranted

        return SpecialAccessItem(
            key = SpecialAccessKey.LOCATION,
            name = "Location Awareness",
            purpose = "Supplies J.A.R.V.I.S with local weather telemetry, city time zones, and location-aware proactive briefings.",
            featureContext = "Geo-Context & Local Weather Telemetry",
            category = AccessCategory.HARDWARE_SENSORS,
            status = if (isGranted) SpecialAccessStatus.GRANTED else SpecialAccessStatus.NOT_GRANTED,
            statusDetail = if (isGranted) "Location telemetry active" else "Location disabled",
            targetSettingsScreen = "App Permissions → Location"
        )
    }

    // --- INTENT DISPATCHER & OEM FALLBACK HANDLERS ---

    /**
     * Attempts to navigate directly to the targeted system settings screen.
     * Uses progressive fallbacks to handle manufacturer differences (Samsung, Xiaomi, Pixel, etc.).
     */
    fun openSettingsForAccess(item: SpecialAccessItem): Boolean {
        val intentList = buildIntentFallbackChain(item)

        for (intent in intentList) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch intent: ${intent.action}, trying next fallback...", e)
            }
        }

        // Ultimate fallback: App Details Settings
        return try {
            val appDetailsIntent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(appDetailsIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "All intent fallbacks failed for ${item.key}", e)
            false
        }
    }

    private fun buildIntentFallbackChain(item: SpecialAccessItem): List<Intent> {
        val pkgUri = Uri.parse("package:${context.packageName}")
        val list = mutableListOf<Intent>()

        when (item.key) {
            SpecialAccessKey.ACCESSIBILITY_SERVICE -> {
                list.add(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            SpecialAccessKey.DISPLAY_OVERLAY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    list.add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri))
                    list.add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
            }

            SpecialAccessKey.NOTIFICATION_LISTENER -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val compName = ComponentName(context, JarvisNotificationListenerService::class.java).flattenToString()
                    list.add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                        putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, compName)
                    })
                }
                list.add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }

            SpecialAccessKey.USAGE_ACCESS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    list.add(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkgUri))
                }
                list.add(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }

            SpecialAccessKey.MODIFY_SYSTEM_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    list.add(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri))
                    list.add(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
                }
            }

            SpecialAccessKey.ALL_FILES_ACCESS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    list.add(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri))
                    list.add(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }

            SpecialAccessKey.DEVICE_ADMIN -> {
                val adminComponent = ComponentName(context, JarvisDeviceAdminReceiver::class.java)
                val adminIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Allows J.A.R.V.I.S to execute emergency security screen lock protocols on voice directive."
                    )
                }
                list.add(adminIntent)
            }

            SpecialAccessKey.BATTERY_OPTIMIZATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    list.add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
                    list.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }

            SpecialAccessKey.EXACT_ALARMS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    list.add(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri))
                }
            }

            SpecialAccessKey.FULL_SCREEN_INTENTS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    list.add(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri))
                }
                list.add(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                })
            }

            SpecialAccessKey.INSTALL_UNKNOWN_APPS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    list.add(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri))
                }
            }

            SpecialAccessKey.VPN_SERVICE -> {
                try {
                    val vpnIntent = VpnService.prepare(context)
                    if (vpnIntent != null) {
                        list.add(vpnIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error preparing VPN intent", e)
                }
            }

            SpecialAccessKey.DND_POLICY_ACCESS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    list.add(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }

            SpecialAccessKey.COMPANION_DEVICE -> {
                list.add(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }

            SpecialAccessKey.NEARBY_DEVICES_BLUETOOTH -> {
                list.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
                list.add(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }

            SpecialAccessKey.POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    list.add(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                }
            }

            SpecialAccessKey.MICROPHONE,
            SpecialAccessKey.CAMERA,
            SpecialAccessKey.CONTACTS,
            SpecialAccessKey.LOCATION -> {
                list.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
            }
        }

        // Global fallback
        list.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
        list.add(Intent(Settings.ACTION_SETTINGS))
        return list
    }

    companion object {
        private const val TAG = "SpecialAccessManager"
    }
}
