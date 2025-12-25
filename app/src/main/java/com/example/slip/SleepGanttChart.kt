package com.example.slip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                .height(245.dp)
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

        val sleepBlocksForDay = sessions.filter { it.category == SleepSession.CATEGORY_SLEEP }
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
    val availableYears = remember(sessions) {
        if (sessions.isEmpty()) listOf(Calendar.getInstance().get(Calendar.YEAR))
        else {
            val cal = Calendar.getInstance()
            sessions.map { 
                cal.timeInMillis = it.startTimeMillis
                cal.get(Calendar.YEAR)
            }.distinct().sortedDescending()
        }
    }
    
    var selectedYear by remember { mutableIntStateOf(availableYears.first()) }
    var showYearMenu by remember { mutableStateOf(false) }

    val totalWeeks = 53
    val daysData = remember(sessions, selectedYear) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
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
            
            val dailySleepSeconds = sessions.filter { it.category == SleepSession.CATEGORY_SLEEP }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sleep Consistency", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            Box {
                Surface(
                    onClick = { showYearMenu = true },
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.height(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedYear.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                    }
                }
                DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false }) {
                    availableYears.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year.toString()) },
                            onClick = {
                                selectedYear = year
                                showYearMenu = false
                            }
                        )
                    }
                }
            }
        }
        
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
                                    sleepSeconds < 6 * 3600 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    sleepSeconds < 8 * 3600 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
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
