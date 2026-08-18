package com.example.services

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class JarvisDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "J.A.R.V.I.S Device Administration protocol enabled.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "J.A.R.V.I.S Device Administration protocol disabled.")
    }

    companion object {
        private const val TAG = "JarvisDeviceAdmin"
    }
}
