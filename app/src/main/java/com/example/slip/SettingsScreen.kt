package com.example.slip

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private enum class TimePickerTarget { WeekdayStart, WeekendStart }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    sessions: List<SleepSession>,
    onSettingsChanged: (UserSettings) -> Unit,
    onAddSession: (SleepSession) -> Unit,
    navController: NavController,
    repository: SleepDataRepository
) {
    val context = LocalContext.current
    var tempSettings by remember(settings) { mutableStateOf(settings) }
    var showTimePickerFor by remember { mutableStateOf<TimePickerTarget?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val isServiceRunning by ScreenMonitorService.isRunning.collectAsState()
    val useUserMl: Boolean by repository.useUserMlModel.collectAsState(initial = false)
    val userMlPath: String? by repository.userMlModelPath.collectAsState(initial = null)
    val userMlMean: Float by repository.userMlMean.collectAsState(initial = 4475.4f)
    val userMlStd: Float by repository.userMlStd.collectAsState(initial = 6533.6f)

    var showDocDialog by remember { mutableStateOf(false) }
    var meanText by remember(userMlMean) { mutableStateOf(userMlMean.toString()) }
    var stdText by remember(userMlStd) { mutableStateOf(userMlStd.toString()) }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val success = importCsv(context, it, onAddSession)
                if (success) {
                    repository.backfillSystemPredictions(context)
                    Toast.makeText(context, "Imported & Backfilled!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val path = repository.saveUserMlModel(context, it)
                if (path != null) {
                    val isValid = withContext(Dispatchers.IO) {
                        val stats = repository.getDurationStats()
                        val classifier = CustomMLClassifier(context, path, stats.first, stats.second)
                        classifier.isValid()
                    }
                    if (isValid) {
                        repository.backfillCustomPredictions(context, path, userMlMean, userMlStd)
                        repository.setUseUserMlModel(true)
                        Toast.makeText(context, "Custom model active & backfilled!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Invalid model format", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    if (showDocDialog) {
        AlertDialog(
            onDismissRequest = { showDocDialog = false },
            confirmButton = { TextButton(onClick = { showDocDialog = false }) { Text("Got it") } },
            title = { Text("How to Train Your Model") },
            text = {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    Text("1. Features (Order matters):", fontWeight = FontWeight.Bold)
                    Text("• start_offset: hours from planned bedtime (-12 to 12)")
                    Text("• end_offset: hours from planned bedtime (-12 to 12)")
                    Text("• duration_z_score: (seconds - mean) / std_dev")
                    Spacer(Modifier.height(8.dp))
                    Text("2. Model Architecture:", fontWeight = FontWeight.Bold)
                    Text("• Input Layer: shape [1, 3]")
                    Text("• Output Layer: shape [1, 1] (Sigmoid probability)")
                    Spacer(Modifier.height(8.dp))
                    Text("3. Scaling Calibration:", fontWeight = FontWeight.Bold)
                    Text("Enter the MEAN and STD DEV from your training script below.")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Schedule", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Weekday", style = MaterialTheme.typography.titleMedium)
                ClickableTimeRow("Bedtime:", tempSettings.weekdaySleepStart) { showTimePickerFor = TimePickerTarget.WeekdayStart }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Weekend", style = MaterialTheme.typography.titleMedium)
                ClickableTimeRow("Bedtime:", tempSettings.weekendSleepStart) { showTimePickerFor = TimePickerTarget.WeekendStart }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI Classification", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showDocDialog = true }) {
                Icon(Icons.Default.Book, "Documentation", tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Custom TFLite Model", fontWeight = FontWeight.Bold)
                        Text(if (userMlPath != null) "Custom model loaded" else "No custom model", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = useUserMl,
                        onCheckedChange = { 
                            if (userMlPath != null) {
                                coroutineScope.launch { repository.setUseUserMlModel(it) }
                            } else {
                                Toast.makeText(context, "Upload a model first", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text("Model Calibration", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = meanText,
                        onValueChange = { meanText = it },
                        label = { Text("Mean") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = stdText,
                        onValueChange = { stdText = it },
                        label = { Text("Std Dev") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                
                TextButton(
                    onClick = {
                        val m = meanText.toFloatOrNull()
                        val s = stdText.toFloatOrNull()
                        if (m != null && s != null) {
                            coroutineScope.launch { 
                                repository.saveUserMlStats(m, s)
                                userMlPath?.let { repository.backfillCustomPredictions(context, it, m, s) }
                            }
                            Toast.makeText(context, "Calibration saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("SAVE CALIBRATION")
                }

                Spacer(Modifier.height(8.dp))
                
                Button(
                    onClick = { modelPickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Icon(Icons.Default.ModelTraining, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload .tflite Model")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Divider()

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:" + context.packageName)
                context.startActivity(intent)
            }) {
                Icon(Icons.Default.Warning, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Permissions")
            }

            Button(
                onClick = {
                    val serviceIntent = Intent(context, ScreenMonitorService::class.java)
                    if (isServiceRunning) {
                        coroutineScope.launch { repository.setMonitoringEnabled(false) }
                        context.stopService(serviceIntent)
                    } else {
                        coroutineScope.launch { repository.setMonitoringEnabled(true) }
                        ContextCompat.startForegroundService(context, serviceIntent)
                    }
                },
                colors = if (isServiceRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors()
            ) {
                Text(if (isServiceRunning) "Stop Monitoring" else "Start Monitoring")
            }

            OutlinedButton(onClick = { csvPickerLauncher.launch("text/*") }) {
                Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import CSV")
            }

            OutlinedButton(onClick = {
                val header = "id,startTimeMillis,endTimeMillis,durationSeconds,isRealSleep,targetBedtimeHour\n"
                val csvContent = sessions.joinToString(separator = "\n") { session ->
                    "${session.id},${session.startTimeMillis},${session.endTimeMillis},${session.durationSeconds},${session.isRealSleep},${session.targetBedtimeHour}"
                }
                val fileName = "sleep_data_${System.currentTimeMillis()}.csv"
                val success = saveTextToFile(context, header + csvContent, fileName)
                Toast.makeText(context, if (success) "Exported CSV" else "Failed", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export CSV")
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
                    TimePickerTarget.WeekendStart -> tempSettings.copy(weekendSleepStart = newTime)
                }
                tempSettings = newSettings
                showTimePickerFor = null
                coroutineScope.launch { onSettingsChanged(newSettings) }
            },
            initialTime = when (target) {
                TimePickerTarget.WeekdayStart -> tempSettings.weekdaySleepStart.toMillis()
                TimePickerTarget.WeekendStart -> tempSettings.weekendSleepStart.toMillis()
            }
        )
    }
}

private suspend fun importCsv(context: android.content.Context, uri: Uri, onAdd: (SleepSession) -> Unit): Boolean = withContext(Dispatchers.IO) {
    try {
        val dateFormat = SimpleDateFormat("MM/dd/yy h:mm a", Locale.US)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val lines = inputStream.bufferedReader().readLines()
            if (lines.size <= 1) return@withContext false
            lines.drop(1).forEach { line ->
                val parts = line.split(",")
                if (parts.size >= 5) {
                    val startMillis = try { parts[1].toLong() } catch (e: Exception) { dateFormat.parse(parts[1])?.time ?: 0L }
                    val endMillis = try { parts[2].toLong() } catch (e: Exception) { dateFormat.parse(parts[2])?.time ?: 0L }
                    val session = SleepSession(
                        id = parts[0],
                        startTimeMillis = startMillis,
                        endTimeMillis = endMillis,
                        durationSeconds = parts[3].toLong(),
                        isRealSleep = parts[4].toBooleanStrictOrNull(),
                        targetBedtimeHour = if (parts.size >= 6) parts[5].toInt() else 22
                    )
                    onAdd(session)
                }
            }
            true
        } ?: false
    } catch (e: Exception) {
        false
    }
}

@Composable
private fun ClickableTimeRow(label: String, time: UserTime, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(time.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
