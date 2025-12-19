package com.example.slip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
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
        const val MONITOR_NOTIFICATION_ID = 2
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
        Log.d("ScreenMonitorService", "✅ SERVICE CREATED: Receiver registered for SCREEN_OFF/USER_PRESENT")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ScreenMonitorService", "🚀 ON_START_COMMAND called")
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setContentTitle("Sleep Monitoring Active")
            .setContentText("Listening for screen lock events...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(MONITOR_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(MONITOR_NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        unregisterReceiver(sleepStateReceiver)
        Log.d("ScreenMonitorService", "🛑 SERVICE DESTROYED: Receiver unregistered")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            "Screen Monitoring Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }
}
