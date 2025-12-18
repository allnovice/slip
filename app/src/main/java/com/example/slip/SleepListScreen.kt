package com.example.slip

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    repository: SleepDataRepository,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var sessionToEdit by remember { mutableStateOf<SleepSession?>(null) }
    var highlightedSessionId by remember { mutableStateOf<String?>(null) }
    
    // --- PERSISTENT FILTER STATE ---
    val minPossible = (sessions.minOfOrNull { it.durationSeconds } ?: 0L).toFloat()
    val maxPossible = (sessions.maxOfOrNull { it.durationSeconds } ?: 3600L).toFloat().coerceAtLeast(minPossible + 1f)
    
    val savedFilterDuration by repository.filterDuration.collectAsState(initial = 0f)
    var filterDurationSeconds by remember { mutableStateOf(0f) }

    // Initialize/Sync from DataStore
    LaunchedEffect(savedFilterDuration) {
        filterDurationSeconds = savedFilterDuration.coerceIn(minPossible, maxPossible)
    }
    
    // Auto-update slider if data changes drastically (e.g. all sessions deleted)
    LaunchedEffect(sessions.size) {
        if (filterDurationSeconds < minPossible) filterDurationSeconds = minPossible
    }

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

    LaunchedEffect(highlightedSessionId) {
        if (highlightedSessionId != null) {
            delay(3000)
            highlightedSessionId = null
        }
    }

    // Filter the sessions based on slider
    val filteredSessions = sessions.filter { it.durationSeconds.toFloat() >= filterDurationSeconds }

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
            Column(modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()) {
                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Lock your phone or press '+' to add a sleep session.")
                    }
                } else {
                    val headerDateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    val groupedSessions = filteredSessions.groupBy { headerDateFormatter.format(it.startTimeMillis) }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item { StatsSection(sessions = sessions, repository = repository) }
                        
                        // --- FILTER SLIDER SECTION ---
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Show sessions longer than: ", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = "${(filterDurationSeconds / 60).toInt()}m",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = filterDurationSeconds,
                                    onValueChange = { filterDurationSeconds = it },
                                    onValueChangeFinished = {
                                        scope.launch {
                                            repository.saveFilterDuration(filterDurationSeconds)
                                        }
                                    },
                                    valueRange = minPossible..maxPossible,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        item { 
                            SleepGanttChart(
                                sessions = sessions,
                                onSessionClick = { session ->
                                    val dateHeader = headerDateFormatter.format(session.startTimeMillis)
                                    // Reset filter if clicked session is filtered out
                                    if (session.durationSeconds < filterDurationSeconds) {
                                        filterDurationSeconds = 0f
                                        scope.launch { repository.saveFilterDuration(0f) }
                                    }
                                    expandedStateMap[dateHeader] = true
                                    highlightedSessionId = session.id
                                    
                                    scope.launch {
                                        var targetIndex = 4 // Offset for Stats, Slider, Chart, Divider
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
                                }
                            ) 
                        }
                        item { Divider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) }

                        groupedSessions.forEach { (dateHeader, sessionsForDate) ->
                            stickyHeader {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = dateHeader, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Icon(
                                        imageVector = if (expandedStateMap.getValue(dateHeader)) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = if (expandedStateMap.getValue(dateHeader)) "Collapse" else "Expand",
                                        modifier = Modifier.clickable { expandedStateMap[dateHeader] = !expandedStateMap.getValue(dateHeader) }
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
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
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

    val baseColor = if (isSleep) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    
    val containerColor = if (isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        baseColor
    }

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
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
