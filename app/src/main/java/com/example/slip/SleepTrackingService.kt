package com.example.slip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar

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
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.value = false
        if (startTime > 0) {
            val endTime = System.currentTimeMillis()
            val seconds = (endTime - startTime) / 1000

            if (seconds < 3600) {
                startTime = 0L
                super.onDestroy()
                return
            }

            val result = runBlocking(Dispatchers.IO) {
                val settings: UserSettings = repository.userSettings.first()
                val systemStats: Pair<Float, Float> = repository.getDurationStats()
                val customPath: String? = repository.userMlModelPath.first()
                val customMean: Float = repository.userMlMean.first()
                val customStd: Float = repository.userMlStd.first()
                val count: Int = repository.getSessionCount()
                
                val cal = Calendar.getInstance().apply { timeInMillis = startTime }
                val isWeekend = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                val targetHour = if (isWeekend) settings.weekendSleepStart.hour else settings.weekdaySleepStart.hour

                val engine = ModelLabEngine(
                    context = applicationContext,
                    settings = settings,
                    systemStats = systemStats,
                    customPath = customPath,
                    customStats = Pair(customMean, customStd),
                    sessionCount = count
                )
                
                val labResult = engine.runAll(startTime, seconds, targetHour)
                Pair(labResult, targetHour)
            }

            val session = SleepSession(
                startTimeMillis = startTime,
                endTimeMillis = endTime,
                durationSeconds = seconds,
                isRealSleep = result.first.dumb,
                targetBedtimeHour = result.second,
                predDefaultMl = result.first.defaultMl,
                predCustomMl = result.first.customMl
            )

            repository.addSleepSession(session)
        }
        startTime = 0L
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(CHANNEL_ID, "Sleep Tracking", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
    }
}
