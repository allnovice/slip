package com.example.slip

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SleepSessionList(
    sessions: List<SleepSession>,
    modifier: Modifier = Modifier,
    onDelete: (SleepSession) -> Unit,
    onEdit: (session: SleepSession, newStart: Long, newEnd: Long, isSleep: Boolean) -> Unit,
    onAdd: (SleepSession) -> Unit,
    onLabel: (session: SleepSession, isRealSleep: Boolean) -> Unit,
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

    if (sessionToEdit != null || showAddDialog) {
        EditSleepDialog(
            session = sessionToEdit,
            onDismiss = {
                sessionToEdit = null
                showAddDialog = false
            },
            onSave = { newStart, newEnd, isSleep ->
                if (sessionToEdit != null) {
                    onEdit(sessionToEdit!!, newStart, newEnd, isSleep)
                } else {
                    val newSession = SleepSession(
                        startTimeMillis = newStart,
                        endTimeMillis = newEnd,
                        durationSeconds = (newEnd - newStart) / 1000,
                        isRealSleep = isSleep
                    )
                    onAdd(newSession)
                }
                sessionToEdit = null
                showAddDialog = false
            }
        )
    }

    val expandedStateMap = remember { mutableStateMapOf<String, Boolean>().withDefault { true } }

    LaunchedEffect(highlightedSessionId, highlightedDateHeader) {
        if (highlightedSessionId != null || highlightedDateHeader != null) {
            delay(3000)
            highlightedSessionId = null
            highlightedDateHeader = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    sessionToEdit = null
                    showAddDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Sleep Session")
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                if (sessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Lock your phone near bedtime to record sleep.")
                    }
                } else {
                    val headerDateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    val groupedSessions = sessions.groupBy { headerDateFormatter.format(it.startTimeMillis) }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item { 
                            StatsSection(
                                sessions = sessions, 
                                repository = repository,
                                onStatusClick = onNavigateToModelLab 
                            ) 
                        }

                        item { 
                            VisualizationPager(
                                sessions = sessions,
                                onSessionClick = { session ->
                                    val dateHeader = headerDateFormatter.format(session.startTimeMillis)
                                    expandedStateMap[dateHeader] = true
                                    highlightedSessionId = session.id
                                    
                                    scope.launch {
                                        var targetIndex = 3 // Offset for Stats, Chart, Divider
                                        for (entry in groupedSessions) {
                                            if (entry.key == dateHeader) {
                                                val sessionIdx = entry.value.indexOfFirst { it.id == session.id }
                                                if (sessionIdx != -1) {
                                                    targetIndex += sessionIdx + 1
                                                    listState.animateScrollToItem(targetIndex)
                                                }
                                                break
                                            }
                                            targetIndex += 1
                                            if (expandedStateMap.getValue(entry.key)) {
                                                targetIndex += entry.value.size
                                            }
                                        }
                                    }
                                },
                                onDayClick = { dayStartMillis ->
                                    val dateHeader = headerDateFormatter.format(dayStartMillis)
                                    expandedStateMap[dateHeader] = true
                                    highlightedDateHeader = dateHeader
                                    
                                    scope.launch {
                                        var targetIndex = 3
                                        for (entry in groupedSessions) {
                                            if (entry.key == dateHeader) {
                                                listState.animateScrollToItem(targetIndex)
                                                break
                                            }
                                            targetIndex += 1
                                            if (expandedStateMap.getValue(entry.key)) {
                                                targetIndex += entry.value.size
                                            }
                                        }
                                    }
                                }
                            ) 
                        }
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) }

                        groupedSessions.forEach { (dateHeader, sessionsForDate) ->
                            stickyHeader {
                                val isHeaderHighlighted = highlightedDateHeader == dateHeader
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isHeaderHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                        .clickable { expandedStateMap[dateHeader] = !expandedStateMap.getValue(dateHeader) }
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = dateHeader, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Icon(
                                        imageVector = if (expandedStateMap.getValue(dateHeader)) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = if (expandedStateMap.getValue(dateHeader)) "Toggle Collapse" else "Toggle Expand"
                                    )
                                }
                            }
                            if (expandedStateMap.getValue(dateHeader)) {
                                items(sessionsForDate, key = { it.id }) { session ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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

        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp)
            )
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
    val isSleep = session.isRealSleep ?: false

    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return "${hours}h ${minutes}m"
    }

    val baseColor = if (isSleep) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest
    val containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else baseColor

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        onClick = onEditClick
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${timeFormatter.format(session.startTimeMillis)} — ${timeFormatter.format(session.endTimeMillis)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = formatDuration(session.durationSeconds),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}
