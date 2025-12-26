package com.example.slip

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SleepSessionList(
    sessions: List<SleepSession>,
    modifier: Modifier = Modifier,
    onDelete: (SleepSession) -> Unit,
    onEdit: (session: SleepSession, newStart: Long, newEnd: Long, category: String) -> Unit,
    onAdd: (SleepSession) -> Unit,
    onLabel: (session: SleepSession, category: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToModelLab: () -> Unit,
    repository: SleepDataRepository,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var sessionToEdit by remember { mutableStateOf<SleepSession?>(null) }
    var highlightedSessionId by remember { mutableStateOf<String?>(null) }
    var highlightedDateHeader by remember { mutableStateOf<String?>(null) }
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val headerDateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val groupedSessions = sessions.groupBy { headerDateFormatter.format(it.startTimeMillis) }

    if (sessionToEdit != null || showAddDialog) {
        EditSleepDialog(
            session = sessionToEdit,
            onDismiss = {
                sessionToEdit = null
                showAddDialog = false
            },
            onSave = { newStart, newEnd, category ->
                if (sessionToEdit != null) {
                    onEdit(sessionToEdit!!, newStart, newEnd, category)
                } else {
                    val newSession = SleepSession(
                        startTimeMillis = newStart,
                        endTimeMillis = newEnd,
                        durationSeconds = (newEnd - newStart) / 1000,
                        category = category,
                        heuristicCategory = category
                    )
                    onAdd(newSession)
                }
                sessionToEdit = null
                showAddDialog = false
            }
        )
    }

    val expandedStateMap = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(highlightedSessionId, highlightedDateHeader) {
        if (highlightedSessionId != null || highlightedDateHeader != null) {
            delay(3000)
            highlightedSessionId = null
            highlightedDateHeader = null
        }
    }

    // Default collapse older than 7 days
    val sevenDaysAgo = remember {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time
    }
    LaunchedEffect(sessions) {
        groupedSessions.keys.forEach { dateStr ->
            if (!expandedStateMap.containsKey(dateStr)) {
                val groupDate = try { headerDateFormatter.parse(dateStr) } catch (e: Exception) { null } ?: Date()
                expandedStateMap[dateStr] = !groupDate.before(sevenDaysAgo)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        sessionToEdit = null
                        showAddDialog = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                if (sessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No logs found.")
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        item { 
                            StatsSection(
                                sessions = sessions, 
                                repository = repository,
                                onStatusClick = onNavigateToModelLab,
                                onSettingsClick = onNavigateToSettings
                            ) 
                        }

                        item { Spacer(Modifier.height(2.dp)) }

                        groupedSessions.forEach { (dateHeader, sessionsForDate) ->
                            stickyHeader {
                                val isHeaderHighlighted = highlightedDateHeader == dateHeader
                                val isExpanded = expandedStateMap[dateHeader] ?: true
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isHeaderHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                        .clickable { expandedStateMap[dateHeader] = !isExpanded }
                                        .padding(horizontal = 16.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = dateHeader, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (expandedStateMap[dateHeader] == true) {
                                items(sessionsForDate, key = { it.id }) { session ->
                                    Box(modifier = Modifier.padding(horizontal = 0.dp)) { // Edge-to-edge
                                        SleepLogRow(
                                            session = session,
                                            isHighlighted = highlightedSessionId == session.id,
                                            onEditClick = { sessionToEdit = session },
                                            onDeleteClick = { onDelete(session) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepLogRow(
    session: SleepSession,
    isHighlighted: Boolean = false,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    val category = session.category

    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    val (icon, tint) = when (category) {
        SleepSession.CATEGORY_SLEEP -> Icons.Default.NightsStay to MaterialTheme.colorScheme.primary
        SleepSession.CATEGORY_NAP -> Icons.Default.Snooze to MaterialTheme.colorScheme.secondary
        else -> Icons.Default.Block to Color.Gray
    }

    val baseColor = if (category == SleepSession.CATEGORY_SLEEP) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest
    val containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else baseColor

    Card(
        modifier = Modifier.fillMaxWidth().height(40.dp), // Compressed height
        shape = RoundedCornerShape(0.dp), // Edge-to-edge look
        colors = CardDefaults.cardColors(containerColor = containerColor),
        onClick = onEditClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT: Icon
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            
            Spacer(Modifier.width(16.dp))

            // CENTER: Time + Category
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${timeFormatter.format(session.startTimeMillis)} - ${timeFormatter.format(session.endTimeMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontSize = 8.sp
                )
            }

            // RIGHT: Duration + Delete
            Text(
                text = formatDuration(session.durationSeconds),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
