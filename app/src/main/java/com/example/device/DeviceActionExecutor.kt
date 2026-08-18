package com.example.device

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ActionResult {
    data class Success(val message: String) : ActionResult()
    data class HandledWithIntent(val message: String, val intentOpened: String) : ActionResult()
    data class RequiresConfirmation(val message: String, val actionToConfirm: () -> Unit) : ActionResult()
    data class Failure(val reason: String) : ActionResult()
}

data class FileSafetyReport(
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    val lastModifiedFormatted: String,
    val isRecentlyModified: Boolean,
    val safetyWarning: String
)

data class StorageStats(
    val totalGb: Double,
    val freeGb: Double,
    val usedGb: Double,
    val usedPercent: Int
)

data class DeviceSpecs(
    val manufacturer: String,
    val model: String,
    val deviceName: String,
    val androidVersion: String,
    val sdkInt: Int,
    val buildId: String,
    val supportedAbis: String
)

class DeviceActionExecutor(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun getBatteryStatus(): String {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else -1

            if (batteryPct >= 0) {
                val state = if (isCharging) "charging" else "discharging"
                "Battery power is at $batteryPct%, currently $state."
            } else {
                "Unable to read battery telemetry at this moment."
            }
        } catch (e: Exception) {
            "Battery status check encountered an issue."
        }
    }

    fun getBatteryPercentage(): Int {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 100
        } catch (e: Exception) {
            100
        }
    }

    fun adjustVolume(increase: Boolean): ActionResult {
        val am = audioManager ?: return ActionResult.Failure("Audio manager is unavailable.")
        return try {
            val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val pct = (currentVol * 100 / maxVol.toFloat()).toInt()
            ActionResult.Success("Volume adjusted to $pct%.")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to adjust volume: ${e.localizedMessage}")
        }
    }

    fun muteVolume(): ActionResult {
        val am = audioManager ?: return ActionResult.Failure("Audio manager is unavailable.")
        return try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            ActionResult.Success("Media volume muted.")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to mute volume.")
        }
    }

    fun launchApplicationByName(appName: String): ActionResult {
        val pm = context.packageManager
        val cleanName = appName.lowercase().trim()

        // Known mapping shortcuts
        val standardPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "camera" to "camera_intent",
            "clock" to "clock_intent",
            "settings" to "settings_intent",
            "calculator" to "calculator_intent",
            "photos" to "photos_intent",
            "gallery" to "photos_intent"
        )

        val target = standardPackages[cleanName]

        if (target == "camera_intent") {
            return launchCamera()
        } else if (target == "settings_intent") {
            return openSettings(Settings.ACTION_SETTINGS, "Android System Settings")
        } else if (target == "clock_intent") {
            return openClock()
        }

        if (target != null) {
            val launchIntent = pm.getLaunchIntentForPackage(target)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ActionResult.Success("Opening $appName.")
            }
        }

        // Search through installed apps
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(cleanName) || cleanName.contains(label)) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return ActionResult.Success("Launching ${pm.getApplicationLabel(app)}.")
                }
            }
        }

        // Web search fallback
        return try {
            val webIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, appName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            ActionResult.HandledWithIntent("Could not find installed app '$appName'. Searching web instead.", "Web Search")
        } catch (e: Exception) {
            ActionResult.Failure("Application '$appName' is not installed on this device.")
        }
    }

    fun launchCamera(): ActionResult {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("Opening camera.")
        } catch (e: Exception) {
            ActionResult.Failure("Could not open camera directly.")
        }
    }

    fun openClock(): ActionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("Opening clock alarms.")
        } catch (e: Exception) {
            openSettings(Settings.ACTION_DATE_SETTINGS, "Date & Time Settings")
        }
    }

    fun setSystemTimer(seconds: Int, message: String): ActionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val minutes = seconds / 60
            ActionResult.Success("Setting a timer for $minutes minutes.")
        } catch (e: Exception) {
            ActionResult.Failure("Timer creation failed or requires clock app permission.")
        }
    }

    fun openSettings(action: String, name: String): ActionResult {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("Opening $name.")
        } catch (e: Exception) {
            ActionResult.Failure("Unable to open $name.")
        }
    }

    fun toggleFlashlight(enable: Boolean): ActionResult {
        val cm = cameraManager ?: return ActionResult.Failure("Camera service is unavailable.")
        return try {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId != null) {
                cm.setTorchMode(cameraId, enable)
                val status = if (enable) "Flashlight activated." else "Flashlight turned off."
                ActionResult.Success(status)
            } else {
                ActionResult.Failure("No flashlight hardware detected on this device.")
            }
        } catch (e: Exception) {
            ActionResult.Failure("Flashlight control requires camera permission or is unavailable.")
        }
    }

    fun inspectFileSafety(filePath: String): FileSafetyReport {
        val file = File(filePath)
        val exists = file.exists()
        val size = if (exists) file.length() else 0L
        val lastMod = if (exists) file.lastModified() else 0L
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedDate = if (lastMod > 0) sdf.format(Date(lastMod)) else "Unknown"

        val isRecent = (System.currentTimeMillis() - lastMod) < (7 * 24 * 60 * 60 * 1000L) // modified in last 7 days

        val warning = when {
            !exists -> "File does not exist at specified path."
            isRecent && size > 1_000_000 -> "Warning: This file was recently modified and is relatively large (${size / 1024} KB). It may contain important user data."
            isRecent -> "Note: This file was recently modified on $formattedDate."
            else -> "Standard file inspection complete."
        }

        return FileSafetyReport(
            fileName = file.name,
            path = file.absolutePath,
            sizeBytes = size,
            lastModifiedFormatted = formattedDate,
            isRecentlyModified = isRecent,
            safetyWarning = warning
        )
    }

    fun getStorageStats(): StorageStats {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - freeBytes

            val bytesInGb = 1024.0 * 1024.0 * 1024.0
            val totalGb = String.format(Locale.US, "%.1f", totalBytes / bytesInGb).toDouble()
            val freeGb = String.format(Locale.US, "%.1f", freeBytes / bytesInGb).toDouble()
            val usedGb = String.format(Locale.US, "%.1f", usedBytes / bytesInGb).toDouble()
            val usedPercent = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0

            StorageStats(totalGb = totalGb, freeGb = freeGb, usedGb = usedGb, usedPercent = usedPercent)
        } catch (e: Exception) {
            StorageStats(totalGb = 64.0, freeGb = 32.0, usedGb = 32.0, usedPercent = 50)
        }
    }

    fun getDeviceSpecs(): DeviceSpecs {
        val abis = Build.SUPPORTED_ABIS.joinToString(", ")
        return DeviceSpecs(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            deviceName = Build.DEVICE,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            buildId = Build.ID,
            supportedAbis = abis
        )
    }

    fun getNetworkStatus(): String {
        val cm = connectivityManager ?: return "Network connectivity status unavailable."
        return try {
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)
            when {
                capabilities == null -> "Device is currently offline (No active internet connection)."
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Connected to Wi-Fi network with active internet access."
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Connected via Cellular Mobile Data."
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Connected via Ethernet."
                else -> "Connected to local network."
            }
        } catch (e: Exception) {
            "Network status inspection complete."
        }
    }

    fun createSystemAlarm(hour: Int, minute: Int, message: String): ActionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val formattedTime = String.format(Locale.US, "%02d:%02d", hour, minute)
            ActionResult.Success("Alarm registered for $formattedTime with label '$message'.")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to schedule alarm: ${e.localizedMessage}")
        }
    }

    fun openWifiSettings(): ActionResult = openSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi Settings")
    fun openBluetoothSettings(): ActionResult = openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings")
    fun openDisplaySettings(): ActionResult = openSettings(Settings.ACTION_DISPLAY_SETTINGS, "Display & Brightness Settings")
    fun openSoundSettings(): ActionResult = openSettings(Settings.ACTION_SOUND_SETTINGS, "Sound & Vibration Settings")
    fun openBatterySettings(): ActionResult = openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS, "Battery & Power Settings")
    fun openStorageSettings(): ActionResult = openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "Internal Storage Settings")
    fun openApplicationSettings(): ActionResult = openSettings(Settings.ACTION_APPLICATION_SETTINGS, "Installed Apps Manager")
    fun openAccessibilitySettings(): ActionResult = openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, "Accessibility Neural Link Settings")
}
