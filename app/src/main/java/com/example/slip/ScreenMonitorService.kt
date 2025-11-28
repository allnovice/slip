package com.example.slip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenMonitorService : Service() {

    private val sleepStateReceiver = SleepStateReceiver()

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
        const val MONITOR_NOTIFICATION_ID = 2 // Use a different ID from the other service
        const val MONITOR_CHANNEL_ID = "ScreenMonitorServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(sleepStateReceiver, intentFilter)
        Log.d("ScreenMonitorService", "Service created and SleepStateReceiver registered.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setContentTitle("Sleep Monitoring Active")
            .setContentText("The app is listening for screen lock events.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW) // A lower priority notification
            .build()

        startForeground(MONITOR_NOTIFICATION_ID, notification)

        // If the service is killed, restart it.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // When the service is destroyed, unregister the receiver to clean up.
        _isRunning.value = false
        unregisterReceiver(sleepStateReceiver)
        Log.d("ScreenMonitorService", "Service destroyed and SleepStateReceiver unregistered.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            "Screen Monitoring Service Channel",
            NotificationManager.IMPORTANCE_LOW // Use low importance for this persistent notification
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }
}
