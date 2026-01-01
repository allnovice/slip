package com.example.slip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*
import kotlin.math.max
import kotlin.math.min

@Composable
fun SleepContributionGraph(
    sessions: List<SleepSession>,
    onDayClick: (Long) -> Unit = {}
) {
    var selectedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }

    val daysData = remember(sessions, selectedYear) {
        val sleepSessions = sessions.filter { it.category == SleepSession.CATEGORY_SLEEP }
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        // Align to the first Sunday to start the grid properly
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) calendar.add(Calendar.DAY_OF_YEAR, -1)
        
        List(53 * 7) {
            val start = calendar.timeInMillis
            val end = start + 24 * 3600 * 1000
            val sleepSecs = sleepSessions.sumOf { s ->
                val oS = max(s.startTimeMillis, start)
                val oE = min(s.endTimeMillis, end)
                if (oS < oE) (oE - oS) / 1000 else 0L
            }
            val result = start to sleepSecs
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            result
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // --- YEAR SELECTOR ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedYear.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
            Row {
                IconButton(onClick = { selectedYear-- }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Year", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { selectedYear++ }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Year", modifier = Modifier.size(20.dp))
                }
            }
        }

        // --- GRID GRAPH ---
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            daysData.chunked(7).forEach { week ->
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    week.forEach { (dayStart, secs) ->
                        val color = when {
                            secs == 0L -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            secs < 4 * 3600 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            secs < 7 * 3600 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .background(color)
                                .clickable { onDayClick(dayStart) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SleepGanttChart(
    sessions: List<SleepSession>,
    onSessionClick: (SleepSession) -> Unit = {}
) {
    val weeklyData = (6 downTo 0).map { daysAgo ->
        val dayCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        dayCalendar.set(Calendar.HOUR_OF_DAY, 0); dayCalendar.set(Calendar.MINUTE, 0); dayCalendar.set(Calendar.SECOND, 0)
        val dayStart = dayCalendar.timeInMillis
        val dayEnd = dayStart + 24 * 3600 * 1000

        sessions.filter { it.category == SleepSession.CATEGORY_SLEEP }
            .mapNotNull { session ->
                val overlapStart = max(session.startTimeMillis, dayStart)
                val overlapEnd = min(session.endTimeMillis, dayEnd)
                if (overlapStart >= overlapEnd) null else {
                    val startOffset = (overlapStart - dayStart) / (24 * 3600 * 1000f)
                    val heightPercent = (overlapEnd - overlapStart) / (24 * 3600 * 1000f)
                    Triple(startOffset, heightPercent, session)
                }
            }
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        weeklyData.forEach { blocks ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                blocks.forEach { (start, height, session) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(height)
                            .offset(y = (start * 200).dp) 
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onSessionClick(session) }
                    )
                }
            }
        }
    }
}
