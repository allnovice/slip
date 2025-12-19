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
    val weekendSleepStart: UserTime
) {
    companion object {
        val default = UserSettings(
            weekdaySleepStart = UserTime(22, 0),  // 10:00 PM
            weekendSleepStart = UserTime(23, 0)   // 11:00 PM
        )
    }
}

/**
 * Checks if the current time is within the monitoring window.
 * Window = 1hr before bedtime until 12 hours later (Automatic Window).
 */
fun UserSettings.isInsideMonitoringWindow(currentTimeMillis: Long): Boolean {
    val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
    val isWeekend = (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    
    val targetBedtime = if (isWeekend) weekendSleepStart else weekdaySleepStart

    val currentMins = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val bedtimeMins = targetBedtime.hour * 60 + targetBedtime.minute
    
    // Start window 1 hour before bedtime
    val startMonitoringMins = (bedtimeMins - 60 + 1440) % 1440
    // End window 12 hours after bedtime
    val endMonitoringMins = (bedtimeMins + 720) % 1440

    return if (startMonitoringMins < endMonitoringMins) {
        currentMins in startMonitoringMins..endMonitoringMins
    } else {
        currentMins >= startMonitoringMins || currentMins <= endMonitoringMins
    }
}

/**
 * The "Dumb Model" logic.
 * Decides if a session should be tagged as "Real Sleep" (✅) by default.
 */
fun UserSettings.isRealSleep(startTimeMillis: Long, durationSeconds: Long): Boolean {
    // --- UPDATED RULE ---
    // For the Dumb Model to auto-tag as "Sleep", it must be longer than 4 hours (14400s).
    // Sessions between 1 and 4 hours will be SAVED to the database (so ML can see them)
    // but they will be tagged as "False" (❌) initially to avoid nap/movie false positives.
    if (durationSeconds < 14400) return false

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
