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

class SleepStateReceiver : BroadcastReceiver() {

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d("SleepStateReceiver", "Screen OFF, attempting to start service.")

                // --- THIS IS THE FIX ---
                // Before starting the tracking service, check for the required permission.
                val permission = Manifest.permission.ACTIVITY_RECOGNITION
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    // If permission is granted, start the service as normal.
                    val serviceIntent = Intent(context, SleepTrackingService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    // If permission is NOT granted, we cannot start the service.
                    // Log an error so the developer can see what happened.
                    Log.e("SleepStateReceiver", "Cannot start SleepTrackingService: ACTIVITY_RECOGNITION permission not granted.")
                }
                // -----------------------
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d("SleepStateReceiver", "Screen ON, stopping service.")
                val serviceIntent = Intent(context, SleepTrackingService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }
}
