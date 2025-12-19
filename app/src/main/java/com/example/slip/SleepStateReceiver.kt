package com.example.slip

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SleepStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // --- PERSISTENCE LOGIC ---
        // If the phone just rebooted, we check if monitoring should be active
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("SleepStateReceiver", "📱 Phone Rebooted. Checking monitoring state...")
            val repository = SleepDataRepository.getInstance(context.applicationContext)
            val shouldMonitor = runBlocking { repository.isMonitoringEnabled.first() }
            
            if (shouldMonitor) {
                Log.d("SleepStateReceiver", "✅ Monitoring was enabled. Restarting ScreenMonitorService.")
                val serviceIntent = Intent(context, ScreenMonitorService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            return
        }

        // --- NORMAL TRACKING LOGIC ---
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d("SleepStateReceiver", "Screen OFF, attempting to start service.")
                val permission = Manifest.permission.ACTIVITY_RECOGNITION
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    val serviceIntent = Intent(context, SleepTrackingService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    Log.e("SleepStateReceiver", "Cannot start SleepTrackingService: Permission not granted.")
                }
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d("SleepStateReceiver", "Screen ON, stopping service.")
                val serviceIntent = Intent(context, SleepTrackingService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }
}
