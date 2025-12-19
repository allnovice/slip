package com.example.slip

import android.icu.util.Calendar
import java.util.Locale

// A simple way to store a time of day
data class UserTime(val hour: Int, val minute: Int) {
    // Helper to format the time for display
    override fun toString(): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm)
    }

    // Helper to convert UserTime to a Long for the time picker
    fun toMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    companion object {
        fun fromString(timeString: String): UserTime {
            return try {
                val parts = timeString.split(":")
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                UserTime(hour, minute)
            } catch (_: Exception) {
                UserTime(0, 0)
            }
        }
    }
}

// The main settings object with default values
data class UserSettings(
    val weekdaySleepStart: UserTime,
    val weekdaySleepEnd: UserTime,
    val weekendSleepStart: UserTime,
    val weekendSleepEnd: UserTime
) {
    companion object {
        val default = UserSettings(
            weekdaySleepStart = UserTime(22, 0),  // 10:00 PM
            weekdaySleepEnd = UserTime(6, 0),    // 6:00 AM
            weekendSleepStart = UserTime(23, 0),  // 11:00 PM
            weekendSleepEnd = UserTime(7, 0)     // 7:00 AM
        )
    }
}

/**
 * Checks if the current time is within the monitoring window (1hr before bedtime to wake-up).
 */
fun UserSettings.isInsideMonitoringWindow(currentTimeMillis: Long): Boolean {
    val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
    val isWeekend = (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    
    val targetBedtime = if (isWeekend) weekendSleepStart else weekdaySleepStart
    val targetWakeup = if (isWeekend) weekendSleepEnd else weekdaySleepEnd

    val currentMins = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val bedtimeMins = targetBedtime.hour * 60 + targetBedtime.minute
    val startMonitoringMins = (bedtimeMins - 60 + 1440) % 1440
    val wakeupMins = targetWakeup.hour * 60 + targetWakeup.minute

    return if (startMonitoringMins < wakeupMins) {
        // Window is within one day (e.g., 9 PM to 11 PM - rare but possible)
        currentMins in startMonitoringMins..wakeupMins
    } else {
        // Window crosses midnight (e.g., 9 PM to 6 AM)
        currentMins >= startMonitoringMins || currentMins <= wakeupMins
    }
}

/**
 * The original heuristic model (Rule-based).
 */
fun UserSettings.isRealSleep(startTimeMillis: Long, durationSeconds: Long): Boolean {
    // Rule 1: The duration must be longer than 1 hour to be considered sleep.
    if (durationSeconds < 3600) return false

    // Rule 2: The start time should be near the user's typical bedtime.
    val startCalendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis }
    val isWeekend = (startCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || startCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    val targetBedtime = if (isWeekend) this.weekendSleepStart else this.weekdaySleepStart
    
    val startHour = startCalendar.get(Calendar.HOUR_OF_DAY)
    val bedtimeHour = targetBedtime.hour

    // Circular check: within 2 hours of bedtime
    var diff = Math.abs(startHour - bedtimeHour)
    if (diff > 12) diff = 24 - diff
    
    return diff <= 2
}
