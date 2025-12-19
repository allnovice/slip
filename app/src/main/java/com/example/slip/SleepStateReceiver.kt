package com.example.slip

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SleepStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = SleepDataRepository.getInstance(context.applicationContext)

        // 1. Handle Reboot
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("SleepStateReceiver", "📱 Phone Rebooted. Checking persistent state...")
            val shouldMonitor = runBlocking { repository.isMonitoringEnabled.first() }
            if (shouldMonitor) {
                val serviceIntent = Intent(context, ScreenMonitorService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            return
        }

        // 2. Handle Screen State
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d("SleepStateReceiver", "Screen OFF event.")
                
                val data = runBlocking {
                    val settings = repository.userSettings.first()
                    val isTracking = SleepTrackingService.isRunning.value
                    Pair(settings, isTracking)
                }

                val settings = data.first
                val alreadyTracking = data.second

                // Only start tracking if we aren't already AND we are inside the window
                // (Window = 1hr before bedtime until wake-up time)
                if (!alreadyTracking && settings.isInsideMonitoringWindow(System.currentTimeMillis())) {
                    val permission = Manifest.permission.ACTIVITY_RECOGNITION
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        Log.d("SleepStateReceiver", "✅ Inside window. Starting SleepTrackingService.")
                        val serviceIntent = Intent(context, SleepTrackingService::class.java)
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } else {
                        Log.e("SleepStateReceiver", "Cannot start: Permission missing.")
                    }
                } else {
                    Log.d("SleepStateReceiver", "🚫 Outside window or already tracking. Ignoring lock.")
                }
            }

            Intent.ACTION_USER_PRESENT -> {
                Log.d("SleepStateReceiver", "Screen ON (Unlocked). Stopping any active tracking.")
                // ACTION_USER_PRESENT always stops the service. 
                // If you sleep past 4 AM, the service remains running until this event triggers.
                val serviceIntent = Intent(context, SleepTrackingService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }
}
