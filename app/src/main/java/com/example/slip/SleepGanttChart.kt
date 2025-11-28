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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

// A data class to hold the processed sleep blocks for drawing
private data class SleepBlock(val startOffsetPercent: Float, val heightPercent: Float)

@Composable
fun SleepGanttChart(sessions: List<SleepSession>) {
    // --- 1. Prepare the data ---
    // The data for each day will now be a list of SleepBlocks
    val weeklyData: List<Pair<String, List<SleepBlock>>> = (6 downTo 0).map { daysAgo ->
        val dayCalendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }

        // Get the label for the day (e.g., "Fri")
        val dayLabel = SimpleDateFormat("EEE", Locale.getDefault()).format(dayCalendar.time)

        // Set the calendar to the start of this day (midnight)
        dayCalendar.set(Calendar.HOUR_OF_DAY, 0)
        dayCalendar.set(Calendar.MINUTE, 0)
        dayCalendar.set(Calendar.SECOND, 0)
        val dayStartMillis = dayCalendar.timeInMillis
        val dayEndMillis = dayStartMillis + 24 * 3600 * 1000

        // Find all sleep sessions that overlap with this day
        val sleepBlocksForDay = sessions.filter { it.isRealSleep == true }
            .mapNotNull { session ->
                // Find the portion of the session that falls within this 24-hour day
                val overlapStart = max(session.startTimeMillis, dayStartMillis)
                val overlapEnd = min(session.endTimeMillis, dayEndMillis)

                if (overlapStart >= overlapEnd) {
                    null // No overlap
                } else {
                    // Calculate the position and height of the sleep block as a percentage of the day
                    val startOffsetMillis = overlapStart - dayStartMillis
                    val durationMillis = overlapEnd - overlapStart

                    val startOffsetPercent = startOffsetMillis / (24 * 3600 * 1000f)
                    val heightPercent = durationMillis / (24 * 3600 * 1000f)

                    SleepBlock(startOffsetPercent, heightPercent)
                }
            }
        Pair(dayLabel, sleepBlocksForDay)
    }

    // --- 2. Draw the chart ---
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 8.dp)
            .height(150.dp), // Fixed height for the chart area
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Bottom
    ) {
        weeklyData.forEach { (day, blocks) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                // This Box represents the full 24-hour day bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f) // The width of the "track"
                        .height(120.dp) // The height of the 24h timeline
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    // For each sleep block, draw a colored Box inside the timeline
                    blocks.forEach { block ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Position the block vertically based on its start time
                                .padding(top = (120 * block.startOffsetPercent).dp)
                                // Set the height of the block based on its duration
                                .height((120 * block.heightPercent).dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
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
