package com.example.slip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
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
    
    val scope = rememberCoroutineScope()
    val totalHistory = sessions.size
    
    // --- ML STATUS LOGIC ---
    val userMlPath by repository.userMlModelPath.collectAsState(initial = null)
    val isModelUploaded = remember(userMlPath) { 
        userMlPath?.let { File(it).exists() } ?: false 
    }

    val targetHours by repository.sleepTargetHours.collectAsState(initial = 7)

    val now = Calendar.getInstance()
    val sevenDaysAgoMillis = Calendar.getInstance().apply { 
        timeInMillis = now.timeInMillis
        add(Calendar.DAY_OF_YEAR, -7) 
    }.timeInMillis

    // Filter for SLEEP sessions in the last 7 days.
    val recentRealSleepSessions = sessions.filter { 
        it.category == SleepSession.CATEGORY_SLEEP && it.startTimeMillis >= sevenDaysAgoMillis 
    }

    // 1. Routine Score (VOLUME BASED: Total Sleep vs Weekly Target)
    val actualSleepSeconds = recentRealSleepSessions.sumOf { it.durationSeconds }
    val weeklyTargetSeconds = targetHours * 7 * 3600L
    
    val weeklyConsistency = if (weeklyTargetSeconds > 0L) {
        (actualSleepSeconds * 100) / weeklyTargetSeconds
    } else 0

    // 2. Avg Sleep Duration (SLEEP only - all time)
    val allRealSleep = sessions.filter { it.category == SleepSession.CATEGORY_SLEEP }
    val avgSleepHours = if (allRealSleep.isNotEmpty()) {
        (allRealSleep.sumOf { it.durationSeconds }.toDouble() / allRealSleep.size) / 3600.0
    } else 0.0

    // 3. Longest/Shortest (SLEEP only - all time)
    val longestSleepHours = (allRealSleep.maxOfOrNull { it.durationSeconds } ?: 0L) / 3600.0
    val shortestSleepHours = (allRealSleep.minOfOrNull { it.durationSeconds } ?: 0L) / 3600.0

    // 4. Last Session Recap (Latest session overall)
    val lastSession = sessions.firstOrNull()
    val lastDurationHours = (lastSession?.durationSeconds ?: 0L) / 3600.0

    Column(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- TOP ROW: ML STATUS & TARGET SELECTOR ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ML Status
            Surface(
                modifier = Modifier.clickable(onClick = onStatusClick),
                shape = CircleShape,
                color = if (isModelUploaded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(if (isModelUploaded) Color(0xFF4CAF50) else Color.Gray)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isModelUploaded) "Model Lab Active" else "Ready to Train",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Target Selector
            Surface(
                onClick = {
                    val nextTarget = if (targetHours >= 9) 7 else targetHours + 1
                    scope.launch { repository.setSleepTargetHours(nextTarget) }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "Goal: ${targetHours}h",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // --- STATS GRID ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(label = "Routine Score", value = "$weeklyConsistency%", unit = "of weekly", modifier = Modifier.weight(1f))
            StatCard(label = "Avg Sleep", value = "%.1f".format(avgSleepHours), unit = "hrs", modifier = Modifier.weight(1f))
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
