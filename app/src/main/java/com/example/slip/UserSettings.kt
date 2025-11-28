package com.example.slip

import android.icu.util.Calendar

// A simple way to store a time of day
// A simple way to store a time of day
data class UserTime(val hour: Int, val minute: Int) {
    // Helper to format the time for display (Your existing code is great)
    override fun toString(): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%d:%02d %s", displayHour, minute, amPm)
    }

    // Helper to convert UserTime to a Long for the time picker
    fun toMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        return calendar.timeInMillis
    }

    // --- THIS IS THE FIX ---
    companion object {
        /**
         * Creates a UserTime object from a "HH:mm" string.
         * This is the function the repository needs.
         */
        fun fromString(timeString: String): UserTime {
            return try {
                val parts = timeString.split(":")
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                UserTime(hour, minute)
            } catch (e: Exception) {
                // If anything goes wrong, return a safe default (midnight).
                UserTime(0, 0)
            }
        }
    }
    // -----------------------
}


// The main settings object with default values
data class UserSettings(
    val weekdaySleepStart: UserTime,
    val weekdaySleepEnd: UserTime,
    val weekendSleepStart: UserTime,
    val weekendSleepEnd: UserTime
) {
    // ... (your existing `isRealSleep` function is correct)

    // --- THIS IS THE FIX for Error 1 ---
    companion object {
        /**
         * Provides a default set of settings for when the app first starts.
         */
        val default = UserSettings(
            weekdaySleepStart = UserTime(22, 0),  // 10:00 PM
            weekdaySleepEnd = UserTime(6, 0),    // 6:00 AM
            weekendSleepStart = UserTime(23, 0),  // 11:00 PM
            weekendSleepEnd = UserTime(7, 0)     // 7:00 AM
        )
    }
    // ------------------------------------
}

// In UserSettings.kt
// ... (UserTime and UserSettings data classes are correct)

// --- ADD THIS NEW FUNCTION ---

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
    // This logic handles "wrap-around" times (e.g., bedtime at 11 PM means 12 AM is "near").
    val isNearBedtime = if (bedtimeHour >= 22) { // For late bedtimes like 10, 11 PM
        (startHour >= bedtimeHour - 2) || (startHour <= (bedtimeHour + 2) % 24)
    } else { // For early bedtimes like 12, 1 AM
        (startHour >= (bedtimeHour - 2 + 24) % 24) || (startHour <= bedtimeHour + 2)
    }

    // A session is only real sleep if it's both long enough AND starts near bedtime.
    return isNearBedtime
}
