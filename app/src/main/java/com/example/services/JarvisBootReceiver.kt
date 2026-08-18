package com.example.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.core.datastore.JarvisPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class JarvisBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i("JarvisBootReceiver", "Boot completed broadcast received. Checking auto-start settings...")
            val repo = JarvisPreferencesRepository(context.applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settings = repo.settingsFlow.first()
                    if (settings.autoStartOnBoot && settings.wakeWordEnabled) {
                        Log.i("JarvisBootReceiver", "Auto-start enabled. Launching J.A.R.V.I.S Background Voice Service.")
                        JarvisVoiceService.startService(context.applicationContext)
                    }
                } catch (e: Exception) {
                    Log.e("JarvisBootReceiver", "Failed to auto-start J.A.R.V.I.S on boot: ${e.localizedMessage}")
                }
            }
        }
    }
}
