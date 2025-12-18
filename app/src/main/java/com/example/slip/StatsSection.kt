package com.example.slip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

@Composable
fun StatsSection(
    sessions: List<SleepSession>,
    repository: SleepDataRepository
) {
    if (sessions.isEmpty()) {
        return
    }
    
    val isMLActive = sessions.size >= 100
    val sevenDaysAgoMillis = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis

    val recentSessions = sessions.filter { it.startTimeMillis >= sevenDaysAgoMillis }
    val recentRealSleepSessions = recentSessions.filter { it.isRealSleep == true }

    val weeklyAverageHours = if (recentRealSleepSessions.isNotEmpty()) {
        (recentRealSleepSessions.sumOf { it.durationSeconds } / 7.0) / 3600.0
    } else {
        0.0
    }

    val longestSleepHours = (recentRealSleepSessions.maxOfOrNull { it.durationSeconds } ?: 0L) / 3600.0
    val shortestSleepHours = (recentRealSleepSessions.minOfOrNull { it.durationSeconds } ?: 0L) / 3600.0

    val totalScreenOffSeconds = recentSessions.sumOf { it.durationSeconds }
    val totalTimeInWeekSeconds = 7 * 24 * 3600
    val avgDailyScreenOnHours = (totalTimeInWeekSeconds - totalScreenOffSeconds).coerceAtLeast(0) / 7.0 / 3600.0

    val avgDailyLocks = if (recentSessions.isNotEmpty()) {
        recentSessions.size / 7.0
    } else {
        0.0
    }

    val todayStartMillis = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }.timeInMillis

    val locksToday = sessions.count { it.startTimeMillis >= todayStartMillis }

    Column(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- ML STATUS BADGE ---
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            shape = CircleShape,
            color = if (isMLActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isMLActive) Color(0xFF4CAF50) else Color.Gray)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isMLActive) "ML Mode: ACTIVE" else "ML Mode: COLLECTING DATA (${sessions.size}/100)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(label = "Avg Sleep", value = "%.1f".format(weeklyAverageHours), unit = "hrs/day", modifier = Modifier.weight(1f))
            StatCard(label = "Screen Time", value = "%.1f".format(avgDailyScreenOnHours), unit = "hrs/day", modifier = Modifier.weight(1f))
            StatCard(label = "Unlocks Today", value = locksToday.toString(), unit = "", modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(label = "Longest Sleep", value = "%.1f".format(longestSleepHours), unit = "hrs", modifier = Modifier.weight(1f))
            StatCard(label = "Shortest Sleep", value = "%.1f".format(shortestSleepHours), unit = "hrs", modifier = Modifier.weight(1f))
            StatCard(label = "Avg Unlocks", value = "%.0f".format(avgDailyLocks), unit = "/day", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 3.dp),
                    maxLines = 1
                )
            }
        }
    }
}
