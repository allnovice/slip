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
        /**
         * Creates a UserTime object from a "HH:mm" string.
         */
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
        /**
         * Provides a default set of settings for when the app first starts.
         */
        val default = UserSettings(
            weekdaySleepStart = UserTime(22, 0),  // 10:00 PM
            weekendSleepStart = UserTime(23, 0)   // 11:00 PM
        )
    }
}

/**
 * The "Dumb Model" or Heuristic.
 * This function checks if a given session qualifies as "real sleep" based on the user's settings. */
fun UserSettings.isRealSleep(startTimeMillis: Long, durationSeconds: Long): Boolean {
    // Rule 1: The duration must be longer than 1 hour to be considered sleep.
    val isLongEnough = durationSeconds > 3600

    if (!isLongEnough) {
        return false
    }

    // Rule 2: The start time should be near the user's typical bedtime.
    val startCalendar =
        Calendar.getInstance().apply { timeInMillis = startTimeMillis }
    val dayOfWeek = startCalendar.get(Calendar.DAY_OF_WEEK)
    val startHour = startCalendar.get(Calendar.HOUR_OF_DAY)

    val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)

    // Determine which bedtime to check against based on the day.
    val targetBedtime = if (isWeekend) this.weekendSleepStart else this.weekdaySleepStart
    val bedtimeHour = targetBedtime.hour

    // Check if the start hour is within a 2-hour window of the target bedtime.
    val isNearBedtime = if (bedtimeHour >= 22) { // For late bedtimes like 10, 11 PM
        (startHour >= bedtimeHour - 2) || (startHour <= (bedtimeHour + 2) % 24)
    } else { // For early bedtimes like 12, 1 AM
        (startHour >= (bedtimeHour - 2 + 24) % 24) || (startHour <= bedtimeHour + 2)
    }

    return isNearBedtime
}
