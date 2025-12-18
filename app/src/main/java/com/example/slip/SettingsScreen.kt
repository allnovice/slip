package com.example.slip

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class TimePickerTarget { WeekdayStart, WeekdayEnd, WeekendStart, WeekendEnd }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    sessions: List<SleepSession>,
    onSettingsChanged: (UserSettings) -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    var tempSettings by remember(settings) { mutableStateOf(settings) }
    var showTimePickerFor by remember { mutableStateOf<TimePickerTarget?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val isServiceRunning by SleepTrackingService.isRunning.collectAsState()

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
                    val serviceIntent = Intent(context, SleepTrackingService::class.java)
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
                val dateFormat = SimpleDateFormat("MM/dd/yy h:mm a", Locale.US)
                val header = "id,startTimeMillis,endTimeMillis,durationSeconds,isRealSleep,targetBedtimeHour\n"
                
                val csvContent = sessions.joinToString(separator = "\n") { session ->
                    val cal = Calendar.getInstance().apply { timeInMillis = session.startTimeMillis }
                    val isWeekend = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                    val targetHour = if (isWeekend) tempSettings.weekendSleepStart.hour else tempSettings.weekdaySleepStart.hour
                    
                    val startStr = dateFormat.format(session.startTimeMillis)
                    val endStr = dateFormat.format(session.endTimeMillis)
                    
                    "${session.id},$startStr,$endStr,${session.durationSeconds},${session.isRealSleep},$targetHour"
                }
                
                val fullCsv = header + csvContent
                val fileName = "sleep_data_universal_${System.currentTimeMillis()}.csv"
                val success = saveTextToFile(context, fullCsv, fileName)
                val message = if (success) "Exported Universal CSV to Downloads" else "CSV Export failed"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.Download, contentDescription = "Export CSV", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export CSV")
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
