package com.example.slip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

private data class SleepBlock(
    val startOffsetPercent: Float,
    val heightPercent: Float,
    val session: SleepSession
)

@Composable
fun VisualizationPager(
    sessions: List<SleepSession>,
    onSessionClick: (SleepSession) -> Unit = {},
    onDayClick: (Long) -> Unit = {}
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) { page ->
            when (page) {
                0 -> SleepGanttChart(sessions, onSessionClick)
                1 -> SleepContributionGraph(sessions, onDayClick)
            }
        }
        
        Row(
            Modifier
                .height(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(2) { iteration ->
                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.LightGray
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                        .size(16.dp, 4.dp)
                )
            }
        }
    }
}

@Composable
fun SleepGanttChart(
    sessions: List<SleepSession>,
    onSessionClick: (SleepSession) -> Unit = {}
) {
    val weeklyData: List<Pair<String, List<SleepBlock>>> = (6 downTo 0).map { daysAgo ->
        val dayCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        val dayLabel = SimpleDateFormat("EEE", Locale.getDefault()).format(dayCalendar.time)
        dayCalendar.set(Calendar.HOUR_OF_DAY, 0); dayCalendar.set(Calendar.MINUTE, 0); dayCalendar.set(Calendar.SECOND, 0)
        val dayStartMillis = dayCalendar.timeInMillis
        val dayEndMillis = dayStartMillis + 24 * 3600 * 1000

        val sleepBlocksForDay = sessions.filter { it.isRealSleep == true }
            .mapNotNull { session ->
                val overlapStart = max(session.startTimeMillis, dayStartMillis)
                val overlapEnd = min(session.endTimeMillis, dayEndMillis)
                if (overlapStart >= overlapEnd) null else {
                    val startOffsetPercent = (overlapStart - dayStartMillis) / (24 * 3600 * 1000f)
                    val heightPercent = (overlapEnd - overlapStart) / (24 * 3600 * 1000f)
                    SleepBlock(startOffsetPercent, heightPercent, session)
                }
            }
        Pair(dayLabel, sleepBlocksForDay)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(150.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Bottom
    ) {
        weeklyData.forEach { (day, blocks) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(120.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    blocks.forEach { block ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = (120 * block.startOffsetPercent).dp)
                                .height((120 * block.heightPercent).dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { onSessionClick(block.session) }
                        )
                    }
                }
                Text(text = day, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun SleepContributionGraph(
    sessions: List<SleepSession>,
    onDayClick: (Long) -> Unit = {}
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val totalWeeks = 53
    
    val daysData = remember(sessions, currentYear) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        
        val data = mutableListOf<Pair<Long, Long>>()
        repeat(totalWeeks * 7) {
            val dayStart = calendar.timeInMillis
            val dayEnd = dayStart + 24 * 3600 * 1000
            
            val dailySleepSeconds = sessions.filter { it.isRealSleep == true }
                .sumOf { session ->
                    val overlapStart = max(session.startTimeMillis, dayStart)
                    val overlapEnd = min(session.endTimeMillis, dayEnd)
                    if (overlapStart < overlapEnd) (overlapEnd - overlapStart) / 1000 else 0L
                }
            
            data.add(Pair(dayStart, dailySleepSeconds))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        data
    }

    val weeks = daysData.chunked(7)
    val monthPositions = remember(weeks) {
        val positions = mutableListOf<Pair<Int, String>>()
        var lastMonth = -1
        val cal = Calendar.getInstance()
        weeks.forEachIndexed { index, week ->
            cal.timeInMillis = week[0].first
            val month = cal.get(Calendar.MONTH)
            if (month != lastMonth) {
                positions.add(index to SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time))
                lastMonth = month
            }
        }
        positions
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Sleep Consistency in $currentYear", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(16.dp))
        
        Row {
            Column(modifier = Modifier.padding(top = 18.dp, end = 8.dp)) {
                Box(modifier = Modifier.height(12.dp))
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.height(12.dp), contentAlignment = Alignment.Center) {
                    Text("Mon", fontSize = 8.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.height(12.dp))
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.height(12.dp), contentAlignment = Alignment.Center) {
                    Text("Wed", fontSize = 8.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.height(12.dp))
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.height(12.dp), contentAlignment = Alignment.Center) {
                    Text("Fri", fontSize = 8.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.height(12.dp))
            }
            
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.horizontalScroll(scrollState)) {
                Box(modifier = Modifier.fillMaxWidth().height(18.dp)) {
                    monthPositions.forEach { (weekIndex, monthName) ->
                        Text(
                            text = monthName,
                            fontSize = 9.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = (weekIndex * 14).dp)
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    weeks.forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            week.forEach { (dayStart, sleepSeconds) ->
                                val color = when {
                                    sleepSeconds == 0L -> MaterialTheme.colorScheme.surfaceContainer
                                    sleepSeconds < 4 * 3600 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    sleepSeconds < 7 * 3600 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(color)
                                        .clickable { onDayClick(dayStart) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
