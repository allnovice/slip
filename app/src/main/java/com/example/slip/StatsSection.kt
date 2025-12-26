package com.example.slip

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale

@OptIn(FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
fun StatsSection(
    sessions: List<SleepSession>,
    repository: SleepDataRepository,
    onStatusClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    if (sessions.isEmpty()) return

    val scope = rememberCoroutineScope()
    val userMlPath by repository.userMlModelPath.collectAsState(initial = null)
    val useUserMl by repository.useUserMlModel.collectAsState(initial = false)
    val modelFileExists = remember(userMlPath) { userMlPath?.let { File(it).exists() } ?: false }
    val isMlActive = modelFileExists && useUserMl

    val targetHours by repository.sleepTargetHours.collectAsState(initial = 7)
    val initialScrollPos by repository.statsScrollPosition.collectAsState(initial = 0)
    val savedPeriods by repository.statsPeriods.collectAsState(initial = emptyMap())
    
    val scrollState = rememberScrollState()
    
    // --- PERSIST SCROLL POSITION ---
    LaunchedEffect(initialScrollPos) {
        if (scrollState.value == 0 && initialScrollPos > 0) scrollState.scrollTo(initialScrollPos)
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .debounce(500)
            .collectLatest { pos ->
                repository.saveStatsScrollPosition(pos)
            }
    }

    // --- PERIOD CALCULATIONS ---
    val now = Calendar.getInstance().timeInMillis
    val sevenDaysAgo = now - (7 * 24 * 3600 * 1000L)
    val thirtyDaysAgo = now - (30 * 24 * 3600 * 1000L)

    fun getStatsForPeriod(periodSessions: List<SleepSession>, targetHrs: Int, fixedWindowDays: Int? = null): Map<String, String> {
        val realSleep = periodSessions.filter { it.category == SleepSession.CATEGORY_SLEEP }
        val naps = periodSessions.filter { it.category == SleepSession.CATEGORY_NAP }
        val actualSecs = realSleep.sumOf { it.durationSeconds }
        val denominatorDays = if (fixedWindowDays != null) fixedWindowDays.toLong() else {
            val firstLog = sessions.lastOrNull()?.startTimeMillis ?: now
            ((now - firstLog) / (24 * 3600 * 1000L)).coerceAtLeast(1)
        }
        val targetSecs = targetHours.toLong() * denominatorDays * 3600L
        val consistency = if (targetSecs > 0L) (actualSecs * 100) / targetSecs else 0
        val avgSleep = if (realSleep.isNotEmpty()) (realSleep.sumOf { it.durationSeconds }.toDouble() / realSleep.size) / 3600.0 else 0.0
        val avgNap = if (naps.isNotEmpty()) (naps.sumOf { it.durationSeconds }.toDouble() / naps.size) / 3600.0 else 0.0
        val shortest = (realSleep.minOfOrNull { it.durationSeconds } ?: 0L) / 3600.0
        val longest = (realSleep.maxOfOrNull { it.durationSeconds } ?: 0L) / 3600.0
        val wakeStr = if (realSleep.isNotEmpty()) {
            val avgMins = realSleep.map {
                val cal = Calendar.getInstance().apply { timeInMillis = it.endTimeMillis }
                cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            }.average().toInt()
            val h = avgMins / 60
            val m = avgMins % 60
            String.format(Locale.getDefault(), "%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
        } else "--:--"
        return mapOf("Routine" to "$consistency%", "Avg Sleep" to "%.1f".format(avgSleep), "Avg Wake" to wakeStr, "Avg Nap" to "%.1f".format(avgNap), "Shortest" to "%.1f".format(shortest), "Longest" to "%.1f".format(longest))
    }

    val stats7d = getStatsForPeriod(sessions.filter { it.startTimeMillis >= sevenDaysAgo }, targetHours, 7)
    val stats30d = getStatsForPeriod(sessions.filter { it.startTimeMillis >= thirtyDaysAgo }, targetHours, 30)
    val statsAll = getStatsForPeriod(sessions, targetHours, null)

    var popupMessage by remember { mutableStateOf<String?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }

    if (popupMessage != null) {
        AlertDialog(onDismissRequest = { popupMessage = null }, confirmButton = { TextButton(onClick = { popupMessage = null }) { Text("Close") } }, text = { Text(popupMessage!!, style = MaterialTheme.typography.bodyMedium) })
    }

    if (showAboutDialog) {
        AlertDialog(onDismissRequest = { showAboutDialog = false }, confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Got it") } }, title = { Text("About SLIP") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SLIP tracks sleep based on screen state and custom ML.", fontSize = 13.sp)
                Text("🌙 Logs: Sessions < 1h are discarded.", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("📊 Cards: Swipe up/down on metric cards to switch periods independently.", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("💡 Memory: All scroll and swipe positions are saved.", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        })
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { // Restored vertical spacing
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp) // Restored spacing between cards
        ) {
            StatCardAction(label = "Model", value = if (isMlActive) "ACTIVE" else "READY", color = if (isMlActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, indicatorColor = if (isMlActive) Color(0xFF4CAF50) else Color.Gray, onClick = onStatusClick)
            StatCardAction(label = "App", value = "CONFIG", icon = Icons.Default.Settings, color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = onSettingsClick)
            StatCardAction(label = "Info", value = "ABOUT", icon = Icons.Default.Info, color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = { showAboutDialog = true })
            StatCardAction(label = "Goal", value = "${targetHours}h", color = MaterialTheme.colorScheme.secondaryContainer, onClick = {
                val nextTarget = if (targetHours >= 9) 7 else targetHours + 1
                scope.launch { repository.setSleepTargetHours(nextTarget) }
            })

            listOf("Routine", "Avg Sleep", "Avg Wake", "Avg Nap", "Shortest", "Longest").forEach { key ->
                val units = mapOf("Routine" to "score", "Avg Sleep" to "hrs", "Avg Nap" to "hrs", "Shortest" to "hrs", "Longest" to "hrs")
                SwipableStatCard(
                    label = key,
                    val7d = stats7d[key] ?: "--",
                    val30d = stats30d[key] ?: "--",
                    valAll = statsAll[key] ?: "--",
                    unit = units[key] ?: "",
                    initialPeriod = savedPeriods[key] ?: 0,
                    onPeriodChanged = { index -> scope.launch { repository.saveStatsPeriod(key, index) } },
                    onClick = { popupMessage = "Showing $key history. Swipe to change period." }
                )
            }
        }
        
        // Heatmap - NO HORIZONTAL PADDING (TOUCHING SIDES)
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            SleepContributionGraph(sessions)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipableStatCard(label: String, val7d: String, val30d: String, valAll: String, unit: String, initialPeriod: Int, onPeriodChanged: (Int) -> Unit, onClick: () -> Unit) {
    val pageCount = 10000 
    val initialPage = (pageCount / 2) - ((pageCount / 2) % 3) + initialPeriod
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
    LaunchedEffect(pagerState.currentPage) { onPeriodChanged(pagerState.currentPage % 3) }
    Card(modifier = Modifier.size(width = 85.dp, height = 75.dp).clickable(onClick = onClick), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(modifier = Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VerticalPager(state = pagerState, modifier = Modifier.height(34.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { page ->
                val actualIdx = page % 3
                val (v, p) = when(actualIdx) { 0 -> val7d to "7d"; 1 -> val30d to "30d"; else -> valAll to "All" }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    Text(p, style = MaterialTheme.typography.labelSmall, fontSize = 7.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                }
            }
            if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
        }
    }
}

@Composable
private fun StatCardAction(label: String, value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, indicatorColor: Color? = null, onClick: () -> Unit) {
    Card(modifier = Modifier.size(width = 85.dp, height = 75.dp).clickable(onClick = onClick), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(modifier = Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (indicatorColor != null) { Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(indicatorColor)); Spacer(Modifier.width(4.dp)) }
                if (icon != null) { Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)) }
                Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
    }
}
