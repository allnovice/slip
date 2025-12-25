package com.example.slip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun SleepBarChart(sessions: List<SleepSession>) {
    // 1. Prepare the data: Group sessions by day for the last 7 days
    val calendar = Calendar.getInstance()
    val weeklyData = (6 downTo 0).map { dayIndex -> // <-- Iterate backwards from 6 to 0
        calendar.time = Date()
        calendar.add(Calendar.DAY_OF_YEAR, -dayIndex) // Subtract the days
        val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dayLabel = dayOfWeekFormat.format(calendar.time)

        val totalSleepSeconds = sessions.filter {
            val sessionCalendar = Calendar.getInstance().apply { timeInMillis = it.startTimeMillis }
            sessionCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                    sessionCalendar.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
        }.filter { it.category == SleepSession.CATEGORY_SLEEP }
            .sumOf { it.durationSeconds }

        val totalSleepHours = totalSleepSeconds / 3600f

        Pair(dayLabel, totalSleepHours)
    }

    // Find the max hours to scale the bars correctly
    val maxHours = (weeklyData.maxOfOrNull { it.second } ?: 8f).coerceAtLeast(8f)

    // 2. Draw the chart
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 8.dp)
            .height(150.dp), // Fixed height for the chart
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Bottom
    ) {
        weeklyData.forEach { (day, hours) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                // Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f) // Bar width
                        .height(((hours / maxHours) * 120).dp) // Bar height scaled to max
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Day Label
                Text(
                    text = day,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
