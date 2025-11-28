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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    // --- THIS IS THE NEW, ELEGANT LAYOUT ---
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            // The Scaffold is now inside the Box and has a transparent background
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            // Restore the single, primary FloatingActionButton
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    sessionToEdit = null
                    showAddDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Sleep Session")
                }
            }
        ) { innerPadding ->
            // The Column and LazyColumn layout for your content is correct.
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
                    val groupedSessions = sessions.groupBy { headerDateFormatter.format(it.startTimeMillis) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item { StatsSection(sessions = sessions, repository = repository) }
                        item { SleepGanttChart(sessions = sessions) }
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
                                items(sessionsForDate) { session ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        SleepLogRow(
                                            session = session,
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

        // The "Settings" button is now an IconButton placed inside the Box, on top of the Scaffold
        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.TopEnd) // Pin it to the top-right corner
                .padding(16.dp) // Give it some space from the edges
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

// ... (Your SleepLogRow function is correct and does not need to change)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepLogRow(
    session: SleepSession,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit // Add the onDeleteClick parameter
) {
    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    val isSleep = session.isRealSleep ?: false

    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return "${hours}h ${minutes}m"
    }

    val cardColors = if (isSleep) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    }

    // Use a Box to allow clicking the main card content to edit,
    // while the delete button has its own separate action.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = cardColors,
        // The main content of the card is still clickable to edit
        onClick = onEditClick
    ) {
        Row(
            // Adjust padding to make room for the icon button at the end
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Column 1: Time Range
            // This Text element will expand to fill the available space.
            Text(
                text = "${timeFormatter.format(session.startTimeMillis)} — ${timeFormatter.format(session.endTimeMillis)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f) // The weight pushes the other items to the right
            )

            // Column 2: Duration
            // This is displayed between the time and the delete button.
            Text(
                text = formatDuration(session.durationSeconds),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp) // Gives it breathing room
            )

            // Column 3: Delete Button
            // The delete button is now part of the Row and will not overlap.
            // Its onClick is separate from the Card's main onClick.
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
