package com.example.slip

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.util.Calendar

private enum class TimePickerTarget {
    WeekdayStart,
    WeekdayEnd,
    WeekendStart,
    WeekendEnd
}

private fun UserTime.toMillis(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, hour)
    calendar.set(Calendar.MINUTE, minute)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalLayoutApi::class) // For FlowRow
@Composable
fun SettingsScreen(
    settings: UserSettings,
    sessions: List<SleepSession>,
    onSettingsChanged: (UserSettings) -> Unit,
    navController: NavController,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    var tempSettings by remember { mutableStateOf(settings) }
    var showTimePickerFor by remember { mutableStateOf<TimePickerTarget?>(null) }

    showTimePickerFor?.let { target ->
        TimePickerDialog(
            onDismiss = { showTimePickerFor = null },
            onConfirm = { hour, minute ->
                val newTime = UserTime(hour, minute)
                tempSettings = when (target) {
                    TimePickerTarget.WeekdayStart -> tempSettings.copy(weekdaySleepStart = newTime)
                    TimePickerTarget.WeekdayEnd -> tempSettings.copy(weekdaySleepEnd = newTime)
                    TimePickerTarget.WeekendStart -> tempSettings.copy(weekendSleepStart = newTime)
                    TimePickerTarget.WeekendEnd -> tempSettings.copy(weekendSleepEnd = newTime)
                }
                showTimePickerFor = null
            },
            initialTime = when (target) {
                TimePickerTarget.WeekdayStart -> tempSettings.weekdaySleepStart.toMillis()
                TimePickerTarget.WeekdayEnd -> tempSettings.weekdaySleepEnd.toMillis()
                TimePickerTarget.WeekendStart -> tempSettings.weekendSleepStart.toMillis()
                TimePickerTarget.WeekendEnd -> tempSettings.weekendSleepEnd.toMillis()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- Schedule Settings ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Weekday", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                ClickableTimeRow("Bedtime:", tempSettings.weekdaySleepStart) { showTimePickerFor = TimePickerTarget.WeekdayStart }
                ClickableTimeRow("Wake-up:", tempSettings.weekdaySleepEnd) { showTimePickerFor = TimePickerTarget.WeekdayEnd }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Weekend", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                ClickableTimeRow("Bedtime:", tempSettings.weekendSleepStart) { showTimePickerFor = TimePickerTarget.WeekendStart }
                ClickableTimeRow("Wake-up:", tempSettings.weekendSleepEnd) { showTimePickerFor = TimePickerTarget.WeekendEnd }
            }
        }

        Spacer(Modifier.weight(1f)) // Pushes buttons to the bottom

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // --- Action Buttons ---
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Button to open system permission settings for the app.
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:" + context.packageName)
                context.startActivity(intent)
            }) {
                Icon(Icons.Default.Warning, contentDescription = "Permissions", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Permissions")
            }

            // The one, useful "Export CSV" button.
            OutlinedButton(onClick = {
                val header = "id,startTimeMillis,endTimeMillis,durationSeconds,isRealSleep\n"
                val csvContent = sessions.joinToString(separator = "\n") { session ->
                    "${session.id},${session.startTimeMillis},${session.endTimeMillis},${session.durationSeconds},${session.isRealSleep}"
                }
                val fullCsv = header + csvContent
                val fileName = "sleep_data_${System.currentTimeMillis()}.csv"

                val success = saveTextToFile(context, fullCsv, fileName)
                val message = if (success) "Exported CSV to Downloads" else "CSV Export failed"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.Download, contentDescription = "Export CSV", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export CSV")
            }

            // The main action button for this screen.
            Button(onClick = {
                onSettingsChanged(tempSettings)
                navController.popBackStack()
            }) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun ClickableTimeRow(label: String, time: UserTime, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(time.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
