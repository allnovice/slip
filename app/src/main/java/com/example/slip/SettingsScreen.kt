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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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

    var customFeatureCount by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(userMlPath) {
        userMlPath?.let { path ->
            val classifier = CustomMLClassifier(context, path, userMlMean, userMlStd)
            customFeatureCount = classifier.inputFeatureCount
        }
    }

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
                        val valid = classifier.isValid()
                        if (valid) customFeatureCount = classifier.inputFeatureCount
                        valid
                    }
                    if (isValid) {
                        repository.backfillCustomPredictions(context, path, userMlMean, userMlStd)
                        repository.setUseUserMlModel(true)
                        Toast.makeText(context, "Custom model active! ($customFeatureCount features detected)", Toast.LENGTH_SHORT).show()
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
            title = { Text("Model Lab: How to Train") },
            text = {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    Text("The app dynamically supports any model trained on the features exported in your CSV.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    
                    Text("1. Input Layer (Order Matters):", fontWeight = FontWeight.Bold)
                    Text("Your model can use 2 or 3 inputs:")
                    Text("• 2 Inputs: [start_offset, end_offset]")
                    Text("• 3 Inputs: [start_offset, end_offset, duration_z_score]")
                    Spacer(Modifier.height(8.dp))
                    
                    Text("2. Definitions:", fontWeight = FontWeight.Bold)
                    Text("• Offsets: Hours from planned bedtime (-12 to 12).")
                    Text("• Z-Score: (seconds - mean) / std_dev.")
                    Spacer(Modifier.height(8.dp))
                    
                    Text("3. Architecture:", fontWeight = FontWeight.Bold)
                    Text("• Output Layer: Must be [1, 1] shape with a Sigmoid activation (probability 0 to 1).")
                    Spacer(Modifier.height(8.dp))
                    
                    Text("Tip:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Clean your CSV of 'noisy' manual edits before training for better accuracy.")
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
                ClickableTimeRow(tempSettings.weekdaySleepStart) { showTimePickerFor = TimePickerTarget.WeekdayStart }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Weekend", style = MaterialTheme.typography.titleMedium)
                ClickableTimeRow(tempSettings.weekendSleepStart) { showTimePickerFor = TimePickerTarget.WeekendStart }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

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
                        Text(
                            text = if (userMlPath != null) "Loaded: $customFeatureCount features detected" else "No custom model",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (userMlPath != null) MaterialTheme.colorScheme.primary else Color.Gray
                        )
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
                
                Text("Model Calibration (For 3-feature models)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = meanText,
                        onValueChange = { meanText = it },
                        label = { Text("Mean") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = customFeatureCount == 3 || userMlPath == null
                    )
                    OutlinedTextField(
                        value = stdText,
                        onValueChange = { stdText = it },
                        label = { Text("Std Dev") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = customFeatureCount == 3 || userMlPath == null
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
                    modifier = Modifier.align(Alignment.End),
                    enabled = customFeatureCount == 3 || userMlPath == null
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
        HorizontalDivider()

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = ("package:" + context.packageName).toUri()
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

@Composable
private fun ClickableTimeRow(time: UserTime, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Bedtime:", style = MaterialTheme.typography.bodyMedium)
        Text(time.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
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
                    val startMillis = try { parts[1].toLong() } catch (_: Exception) { dateFormat.parse(parts[1])?.time ?: 0L }
                    val endMillis = try { parts[2].toLong() } catch (_: Exception) { dateFormat.parse(parts[2])?.time ?: 0L }
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
    } catch (_: Exception) {
        false
    }
}
