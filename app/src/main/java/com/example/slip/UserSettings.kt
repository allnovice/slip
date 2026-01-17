package com.example.slip

import android.icu.util.Calendar
import java.util.Locale

// A simple way to store a time of day
data class UserTime(val hour: Int, val minute: Int) {
    override fun toString(): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm)
    }

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
                UserTime(parts[0].toInt(), parts[1].toInt())
            } catch (_: Exception) {
                UserTime(22, 0)
            }
        }
    }
}

data class UserSettings(
    val baseBedtime: UserTime,
    val offDays: Set<Int> = setOf(Calendar.SATURDAY, Calendar.SUNDAY) // Default OFF: Sat, Sun
) {
    companion object {
        val default = UserSettings(baseBedtime = UserTime(22, 0))
    }

    /**
     * Calculates the target bedtime for a given start time.
     * Logic: If tomorrow is an OFF day, use baseBedtime + 2 hours.
     */
    fun getTargetHourFor(startTimeMillis: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = startTimeMillis }
        
        // Move to "Tomorrow" to see if we should stay up late tonight
        val tomorrowCalendar = Calendar.getInstance().apply { 
            timeInMillis = startTimeMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val tomorrowDay = tomorrowCalendar.get(Calendar.DAY_OF_WEEK)

        return if (offDays.contains(tomorrowDay)) {
            (baseBedtime.hour + 2) % 24
        } else {
            baseBedtime.hour
        }
    }
}

/**
 * Checks if the current time is within the monitoring window.
 * Now uses the dynamic getTargetHourFor logic.
 */
fun UserSettings.isInsideMonitoringWindow(currentTimeMillis: Long): Boolean {
    val targetHour = getTargetHourFor(currentTimeMillis)
    val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
    val currentMins = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val bedtimeMins = targetHour * 60 + baseBedtime.minute
    
    val startMonitoringMins = (bedtimeMins - 120 + 1440) % 1440
    val endMonitoringMins = (bedtimeMins + 720) % 1440

    return if (startMonitoringMins < endMonitoringMins) {
        currentMins in startMonitoringMins..endMonitoringMins
    } else {
        currentMins >= startMonitoringMins || currentMins <= endMonitoringMins
    }
}
