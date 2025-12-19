package com.example.slip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    repository: SleepDataRepository,
    onStatusClick: () -> Unit
) {
    if (sessions.isEmpty()) {
        return
    }
    
    val totalHistory = sessions.size
    val isSystemMlActive = totalHistory >= 100
    val sevenDaysAgoMillis = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis

    // Filter for sessions in the last 7 days.
    val recentSessions = sessions.filter { it.startTimeMillis >= sevenDaysAgoMillis }
    val recentRealSleepSessions = recentSessions.filter { it.isRealSleep == true }

    // 1. Routine Consistency (How often we match the 'Dumb' model rules)
    val weeklyConsistency = if (recentSessions.isNotEmpty()) {
        val onTimeSlept = recentSessions.count { it.isRealSleep == true }
        (onTimeSlept * 100) / recentSessions.size
    } else 0

    // 2. Avg Sleep Duration
    val weeklyAverageHours = if (recentRealSleepSessions.isNotEmpty()) {
        (recentRealSleepSessions.sumOf { it.durationSeconds }.toDouble() / recentRealSleepSessions.size) / 3600.0
    } else 0.0

    // 3. Longest/Shortest
    val longestSleepHours = (recentRealSleepSessions.maxOfOrNull { it.durationSeconds } ?: 0L) / 3600.0
    val shortestSleepHours = (recentRealSleepSessions.minOfOrNull { it.durationSeconds } ?: 0L) / 3600.0

    // 4. Last Night Recap
    val lastSession = sessions.firstOrNull()
    val lastDurationHours = (lastSession?.durationSeconds ?: 0L) / 3600.0

    Column(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- ML STATUS BADGE ---
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable(onClick = onStatusClick),
            shape = CircleShape,
            color = if (isSystemMlActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isSystemMlActive) Color(0xFF4CAF50) else Color.Gray)
                )
                Spacer(Modifier.width(8.dp))
                val statusText = if (isSystemMlActive) "Model Lab Active" else "Training Model ($totalHistory/100)"
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // --- STATS GRID ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(label = "Routine Score", value = "$weeklyConsistency%", unit = "match", modifier = Modifier.weight(1f))
            StatCard(label = "Avg Session", value = "%.1f".format(weeklyAverageHours), unit = "hrs", modifier = Modifier.weight(1f))
            StatCard(label = "Last Session", value = "%.1f".format(lastDurationHours), unit = "hrs", modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(label = "Longest", value = "%.1f".format(longestSleepHours), unit = "hrs", modifier = Modifier.weight(1f))
            StatCard(label = "Shortest", value = "%.1f".format(shortestSleepHours), unit = "hrs", modifier = Modifier.weight(1f))
            StatCard(label = "Total Logs", value = totalHistory.toString(), unit = "total", modifier = Modifier.weight(1f))
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
