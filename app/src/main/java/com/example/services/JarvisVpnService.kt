package com.example.services

import android.content.Intent
import android.net.VpnService
import android.util.Log

class JarvisVpnService : VpnService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "J.A.R.V.I.S Privacy Sentinel VPN service invoked.")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "J.A.R.V.I.S Privacy Sentinel VPN service destroyed.")
    }

    companion object {
        private const val TAG = "JarvisVpnService"
    }
}
