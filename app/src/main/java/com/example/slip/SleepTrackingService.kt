package com.example.slip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SleepTrackingService : Service() {

    private val repository: SleepDataRepository by lazy { SleepDataRepository.getInstance(applicationContext) }
    private var startTime: Long = 0

    companion object {
        val isRunning = MutableStateFlow(false)
        const val TRACKING_NOTIFICATION_ID = 1
        const val INTERACTIVE_NOTIFICATION_ID = 2
        const val CHANNEL_ID = "SleepTrackingServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (startTime == 0L) {
            startTime = System.currentTimeMillis()
            createNotificationChannel()
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sleep Tracking Active")
                .setContentText("Your sleep session is being recorded.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
            startForeground(TRACKING_NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.value = false
        if (startTime > 0) {
            val endTime = System.currentTimeMillis()
            val seconds = (endTime - startTime) / 1000

            // Skip anything less than 1 hour
            if (seconds < 3600) {
                Log.d("SleepTrackingService", "Session too short ($seconds s). Discarding.")
                startTime = 0L
                super.onDestroy()
                return
            }

            val result = runBlocking(Dispatchers.IO) {
                val settings: UserSettings = repository.userSettings.first()
                val customPath: String? = repository.userMlModelPath.first()
                val customMean: Float = repository.userMlMean.first()
                val customStd: Float = repository.userMlStd.first()
                
                val targetHour = settings.getTargetHourFor(startTime)

                // 1. Calculate PURE Heuristic (Dumb Model / The Standard)
                val heuristicClassifier = HeuristicClassifier(settings)
                val rawHeuristic = heuristicClassifier.classify(startTime, seconds, targetHour)

                // 2. Calculate ML Engine Result (The Competitor - for logging/lab only)
                val engine = ModelLabEngine(
                    settings = settings,
                    customPath = customPath,
                    customMean = customMean,
                    customStd = customStd
                )
                val mlGuess = engine.runAll(startTime, seconds, targetHour)
                
                Log.d("SleepTrackingService", "Session End. Standard (Base): $rawHeuristic, ML Competitor: $mlGuess")
                
                Triple(mlGuess, rawHeuristic, targetHour)
            }
            
            // Truth and Base both start with the Rules (The Standard)
            val session = SleepSession(
                startTimeMillis = startTime,
                endTimeMillis = endTime,
                durationSeconds = seconds,
                category = result.second,          // TRUTH starts as Base Rules
                heuristicCategory = result.second, // BASE is always the Rules
                targetBedtimeHour = result.third
            )

            repository.addSleepSession(session)

            // ONLY trigger Nap Notification if the Standard (Rules) detects a nap.
            // This ensures the notification is confirming the database's initial state.
            if (result.second == SleepSession.CATEGORY_NAP) {
                sendNapConfirmationNotification(session)
            }
        }
        startTime = 0L
        super.onDestroy()
    }

    private fun sendNapConfirmationNotification(session: SleepSession) {
        val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeRange = "${timeFormatter.format(session.startTimeMillis)} - ${timeFormatter.format(session.endTimeMillis)}"
        
        val confirmIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = "ACTION_CONFIRM_NAP"
            putExtra("session_id", session.id)
            putExtra("notification_id", INTERACTIVE_NOTIFICATION_ID)
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(this, 0, confirmIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val idleIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = "ACTION_MARK_IDLE"
            putExtra("session_id", session.id)
            putExtra("notification_id", INTERACTIVE_NOTIFICATION_ID)
        }
        val idlePendingIntent = PendingIntent.getBroadcast(this, 1, idleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Did you just take a nap?")
            .setContentText("Detected session: $timeRange")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher_foreground, "Yes, it was a nap", confirmPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "No, phone was idle", idlePendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(INTERACTIVE_NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(CHANNEL_ID, "Sleep Tracking", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
    }
}
