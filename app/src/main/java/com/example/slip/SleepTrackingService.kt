package com.example.slip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SleepTrackingService : Service() {

    private val repository: SleepDataRepository by lazy { SleepDataRepository.getInstance(applicationContext) }

    private var startTime: Long = 0

    companion object {
        val isRunning = MutableStateFlow(false)
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "SleepTrackingServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
        Log.d("SleepTrackingService", "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (startTime == 0L) {
            startTime = System.currentTimeMillis()
            Log.d("SleepTrackingService", "Sleep tracking STARTED at: $startTime")

            createNotificationChannel()
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sleep Tracking Active")
                .setContentText("Your sleep session is being recorded.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } else {
            Log.d("SleepTrackingService", "Service already running. Ignoring duplicate start command.")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.value = false
        if (startTime > 0) {
            val endTime = System.currentTimeMillis()
            val seconds = (endTime - startTime) / 1000

            // --- DYNAMIC CLASSIFICATION ---
            val classifierData = runBlocking(Dispatchers.IO) {
                val settings = repository.userSettings.first()
                val count = repository.getSessionCount()
                val stats = repository.getDurationStats() // Get Dynamic Stats for Universal Scaling
                Triple(settings, count, stats)
            }

            val classifier = DynamicSleepClassifier(
                context = applicationContext,
                settings = classifierData.first,
                sessionCount = classifierData.second,
                stats = classifierData.third
            )

            val isSleep: Boolean = classifier.isRealSleep(startTimeMillis = startTime, durationSeconds = seconds, statsArg = classifierData.third)

            Log.d("SleepTrackingService", "Session of $seconds s. Count: ${classifierData.second}. ML active? ${classifierData.second >= 100}. Sleep? $isSleep.")

            val session = SleepSession(
                startTimeMillis = startTime,
                endTimeMillis = endTime,
                durationSeconds = seconds,
                isRealSleep = isSleep
            )

            repository.addSleepSession(session)
        }
        startTime = 0L
        super.onDestroy()
        Log.d("SleepTrackingService", "Service Destroyed and session saved.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Sleep Tracking Service Channel",
            NotificationManager.IMPORTANCE_MIN
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }
}
