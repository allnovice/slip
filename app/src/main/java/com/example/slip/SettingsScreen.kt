package com.example.slip

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private enum class TimePickerTarget { WeekdayStart, WeekdayEnd, WeekendStart, WeekendEnd }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    sessions: List<SleepSession>,
    onSettingsChanged: (UserSettings) -> Unit,
    onAddSession: (SleepSession) -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    var tempSettings by remember(settings) { mutableStateOf(settings) }
    var showTimePickerFor by remember { mutableStateOf<TimePickerTarget?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val isServiceRunning by ScreenMonitorService.isRunning.collectAsState()

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val success = importCsv(context, it, onAddSession)
                if (success) {
                    Toast.makeText(context, "Data imported successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to import CSV", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    showTimePickerFor?.let { target ->
        TimePickerDialog(
            onDismiss = { showTimePickerFor = null },
            onConfirm = { hour, minute ->
                val newTime = UserTime(hour, minute)
                val newSettings = when (target) {
                    TimePickerTarget.WeekdayStart -> tempSettings.copy(weekdaySleepStart = newTime)
                    TimePickerTarget.WeekdayEnd -> tempSettings.copy(weekdaySleepEnd = newTime)
                    TimePickerTarget.WeekendStart -> tempSettings.copy(weekendSleepStart = newTime)
                    TimePickerTarget.WeekendEnd -> tempSettings.copy(weekendSleepEnd = newTime)
                }
                tempSettings = newSettings
                showTimePickerFor = null

                coroutineScope.launch {
                    onSettingsChanged(newSettings)
                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                }
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

        Spacer(Modifier.weight(1f))
        Divider(modifier = Modifier.padding(vertical = 16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:" + context.packageName)
                context.startActivity(intent)
            }) {
                Icon(Icons.Default.Warning, contentDescription = "Permissions", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Permissions")
            }

            Button(
                onClick = {
                    val serviceIntent = Intent(context, ScreenMonitorService::class.java)
                    if (isServiceRunning) {
                        context.stopService(serviceIntent)
                    } else {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    }
                },
                colors = if (isServiceRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors()
            ) {
                Text(if (isServiceRunning) "Stop Monitoring" else "Start Monitoring")
            }

            OutlinedButton(onClick = {
                csvPickerLauncher.launch("text/*")
            }) {
                Icon(Icons.Default.Upload, contentDescription = "Import CSV", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import CSV")
            }

            OutlinedButton(onClick = {
                val header = "id,startTimeMillis,endTimeMillis,durationSeconds,isRealSleep,targetBedtimeHour\n"
                val csvContent = sessions.joinToString(separator = "\n") { session ->
                    "${session.id},${session.startTimeMillis},${session.endTimeMillis},${session.durationSeconds},${session.isRealSleep},${session.targetBedtimeHour}"
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
        }
    }
}

private suspend fun importCsv(context: android.content.Context, uri: Uri, onAdd: (SleepSession) -> Unit): Boolean = withContext(Dispatchers.IO) {
    try {
        val dateFormat = SimpleDateFormat("MM/dd/yy h:mm a", Locale.US)
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = inputStream.bufferedReader()
            val lines = reader.readLines()
            if (lines.size <= 1) return@withContext false

            lines.drop(1).forEach { line ->
                val parts = line.split(",")
                if (parts.size >= 5) {
                    val startMillis = try {
                        parts[1].toLong()
                    } catch (e: NumberFormatException) {
                        dateFormat.parse(parts[1])?.time ?: System.currentTimeMillis()
                    }
                    
                    val endMillis = try {
                        parts[2].toLong()
                    } catch (e: NumberFormatException) {
                        dateFormat.parse(parts[2])?.time ?: System.currentTimeMillis()
                    }

                    val targetHour = if (parts.size >= 6) parts[5].toInt() else 22
                    val session = SleepSession(
                        id = parts[0],
                        startTimeMillis = startMillis,
                        endTimeMillis = endMillis,
                        durationSeconds = parts[3].toLong(),
                        isRealSleep = parts[4].toBooleanStrictOrNull(),
                        targetBedtimeHour = targetHour
                    )
                    onAdd(session)
                }
            }
            true
        } ?: false
    } catch (e: Exception) {
        e.printStackTrace()
        false
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
