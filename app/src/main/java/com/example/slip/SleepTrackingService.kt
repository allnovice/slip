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

    // --- THIS IS THE FIX ---
    // Change this line to use the new Singleton getInstance() method.
    // This ensures the service uses the EXACT SAME repository instance as the MainActivity.
    private val repository: SleepDataRepository by lazy { SleepDataRepository.getInstance(applicationContext) }
    // -----------------------

    private var startTime: Long = 0

    companion object {
        // This Flow will tell the UI if the service is active. It's public and static.
        val isRunning = MutableStateFlow(false)
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "SleepTrackingServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true // Set to true when service is created
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
                .setPriority(NotificationCompat.PRIORITY_MIN) // <-- Set to MIN
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } else {
            // If startTime is not 0, it means the service is already running.
            // We ignore this duplicate start command.
            Log.d("SleepTrackingService", "Service already running. Ignoring duplicate start command.")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.value = false
        if (startTime > 0) {
            val endTime = System.currentTimeMillis()
            val seconds = (endTime - startTime) / 1000

            // --- THIS IS THE NEW, CORRECT LOGIC ---
            // We log EVERY session and tag it as TRUE or FALSE. No more skipping or nulls.

            // Get user settings to auto-tag the session.
            val userSettings = runBlocking(Dispatchers.IO) {
                repository.userSettings.first()
            }

            // We use the schedule to determine if the session was likely sleep (true) or not (false).
            // This runs for EVERY session, regardless of its length.
            val isSleep: Boolean = userSettings.isRealSleep(startTimeMillis = startTime, durationSeconds = seconds)

            Log.d("SleepTrackingService", "Session of $seconds s. Auto-tagged as sleep? $isSleep. SAVING.")

            val session = SleepSession(
                startTimeMillis = startTime,
                endTimeMillis = endTime,
                durationSeconds = seconds,
                isRealSleep = isSleep // The label will always be true or false.
            )

            repository.addSleepSession(session)
            // ------------------------------------
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
