package com.example.slip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSleepDialog(
    session: SleepSession?,
    onDismiss: () -> Unit,
    onSave: (newStartTimeMillis: Long, newEndTimeMillis: Long, isRealSleep: Boolean) -> Unit
) {
    val initialStartTime = session?.startTimeMillis ?: System.currentTimeMillis()
    val initialEndTime = session?.endTimeMillis ?: (System.currentTimeMillis() + 8 * 3600 * 1000)

    var tempStartTime by remember { mutableStateOf(initialStartTime) }
    var tempEndTime by remember { mutableStateOf(initialEndTime) }
    var tempIsSleep by remember { mutableStateOf(session?.isRealSleep ?: true) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    // --- DIALOGS ---

    if (showDatePicker) {
        CalendarDatePickerDialog(
            onDismiss = { showDatePicker = false },
            onDateSelected = { selectedDateMillis ->
                // When a new date is picked, update the base day for both start and end times
                val startCal = Calendar.getInstance().apply { timeInMillis = tempStartTime }
                val endCal = Calendar.getInstance().apply { timeInMillis = tempEndTime }

                val newStartCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                newStartCal.set(Calendar.HOUR_OF_DAY, startCal.get(Calendar.HOUR_OF_DAY))
                newStartCal.set(Calendar.MINUTE, startCal.get(Calendar.MINUTE))
                tempStartTime = newStartCal.timeInMillis

                val newEndCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                newEndCal.set(Calendar.HOUR_OF_DAY, endCal.get(Calendar.HOUR_OF_DAY))
                newEndCal.set(Calendar.MINUTE, endCal.get(Calendar.MINUTE))
                tempEndTime = newEndCal.timeInMillis

                // Now, re-check if the end time needs to be pushed to the next day
                if (tempEndTime <= tempStartTime) {
                    val endCalendar = Calendar.getInstance().apply { timeInMillis = tempEndTime }
                    endCalendar.add(Calendar.DAY_OF_YEAR, 1)
                    tempEndTime = endCalendar.timeInMillis
                }

                showDatePicker = false
            },
            initialDateMillis = tempStartTime
        )
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            onDismiss = { showStartTimePicker = false },
            onConfirm = { hour, minute ->
                val newStartCal = Calendar.getInstance().apply { timeInMillis = tempStartTime }
                newStartCal.set(Calendar.HOUR_OF_DAY, hour)
                newStartCal.set(Calendar.MINUTE, minute)
                tempStartTime = newStartCal.timeInMillis

                // After changing start time, re-check if end time needs to be adjusted
                if (tempEndTime <= tempStartTime) {
                    val endCalendar = Calendar.getInstance().apply { timeInMillis = tempEndTime }
                    endCalendar.add(Calendar.DAY_OF_YEAR, 1)
                    tempEndTime = endCalendar.timeInMillis
                }
                showStartTimePicker = false
            },
            initialTime = tempStartTime
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            onDismiss = { showEndTimePicker = false },
            onConfirm = { hour, minute ->
                val newEndCal = Calendar.getInstance().apply { timeInMillis = tempStartTime } // Base it on start time's day
                newEndCal.set(Calendar.HOUR_OF_DAY, hour)
                newEndCal.set(Calendar.MINUTE, minute)
                var newEndTime = newEndCal.timeInMillis

                // If the new end time is before the start time, it must be the next day.
                if (newEndTime <= tempStartTime) {
                    newEndCal.add(Calendar.DAY_OF_YEAR, 1)
                    newEndTime = newEndCal.timeInMillis
                }
                tempEndTime = newEndTime
                showEndTimePicker = false
            },
            initialTime = tempEndTime
        )
    }

    // --- Main Dialog UI ---
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (session == null) "Add New Session" else "Edit Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
                // The UI rows are correct
                ClickableFieldRow(label = "Date:", value = dateFormatter.format(tempStartTime), onClick = { showDatePicker = true })
                Divider()
                ClickableFieldRow(label = "Start:", value = timeFormatter.format(tempStartTime), onClick = { showStartTimePicker = true })
                ClickableFieldRow(label = "End:", value = timeFormatter.format(tempEndTime), onClick = { showEndTimePicker = true })
                Divider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Mark as real sleep?")
                    Switch(checked = tempIsSleep, onCheckedChange = { tempIsSleep = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(tempStartTime, tempEndTime, tempIsSleep) }) { Text("Save") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit, // Changed to return hour and minute
    initialTime: Long
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initialTime }
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time") },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { TimePicker(state = timePickerState) } },
        confirmButton = {
            Button(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute) // Pass back just the hour and minute
            }) { Text("OK") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )
}

// --- THIS IS THE FIX: The bodies of these helper functions are now filled in ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDatePickerDialog(
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit,
    initialDateMillis: Long
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                datePickerState.selectedDateMillis?.let { onDateSelected(it) }
            }) { Text("OK") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun ClickableFieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
