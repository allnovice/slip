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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

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
    var showTimePicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val isServiceRunning by ScreenMonitorService.isRunning.collectAsState()
    val useUserMl by repository.useUserMlModel.collectAsState(initial = false)
    val userMlPath by repository.userMlModelPath.collectAsState(initial = null)
    val userMlMean by repository.userMlMean.collectAsState(initial = 4475.4f)
    val userMlStd by repository.userMlStd.collectAsState(initial = 6533.6f)

    var showDocDialog by remember { mutableStateOf(false) }
    var meanText by remember(userMlMean) { mutableStateOf(userMlMean.toString()) }
    var stdText by remember(userMlStd) { mutableStateOf(userMlStd.toString()) }

    var customFeatureCount by remember { mutableIntStateOf(0) }
    val modelExists = remember(userMlPath) { userMlPath?.let { File(it).exists() } ?: false }
    
    LaunchedEffect(userMlPath, modelExists) {
        val path = userMlPath
        if (modelExists && path != null) {
            val classifier = UserCustomClassifier(path, userMlMean, userMlStd)
            customFeatureCount = classifier.inputFeatureCount
        } else {
            customFeatureCount = 0
        }
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val success = importCsv(context, it, onAddSession, settings)
                if (success) {
                    Toast.makeText(context, "Imported!", Toast.LENGTH_SHORT).show()
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
                        val classifier = UserCustomClassifier(path, stats.first, stats.second)
                        val valid = classifier.isValid()
                        if (valid) customFeatureCount = classifier.inputFeatureCount
                        valid
                    }
                    if (isValid) {
                        repository.setUseUserMlModel(true)
                        Toast.makeText(context, "Model active!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Invalid format (Requires 3 outputs)", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- 1. SCHEDULE SECTION ---
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Schedule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showTimePicker = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Standard Bedtime", style = MaterialTheme.typography.bodyLarge)
                        Text(tempSettings.baseBedtime.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    
                    HorizontalDivider()
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select your OFF days (Bedtime shifts +2h tonight)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            val days = listOf(
                                "S" to android.icu.util.Calendar.SUNDAY,
                                "M" to android.icu.util.Calendar.MONDAY,
                                "T" to android.icu.util.Calendar.TUESDAY,
                                "W" to android.icu.util.Calendar.WEDNESDAY,
                                "T" to android.icu.util.Calendar.THURSDAY,
                                "F" to android.icu.util.Calendar.FRIDAY,
                                "S" to android.icu.util.Calendar.SATURDAY
                            )
                            days.forEach { (label, dayConst) ->
                                val isSelected = tempSettings.offDays.contains(dayConst)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val newOffDays = if (isSelected) tempSettings.offDays - dayConst else tempSettings.offDays + dayConst
                                        val newSettings = tempSettings.copy(offDays = newOffDays)
                                        tempSettings = newSettings
                                        coroutineScope.launch { onSettingsChanged(newSettings) }
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    modifier = Modifier.size(width = 44.dp, height = 40.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. AI SECTION ---
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("User ML Model", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showDocDialog = true }) { Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.primary) }
            }
            
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TFLite Classifier", fontWeight = FontWeight.Bold)
                            Text(
                                text = if (modelExists) "Loaded ($customFeatureCount features)" else "No model uploaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (modelExists) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        Switch(
                            checked = useUserMl && modelExists,
                            onCheckedChange = { 
                                if (modelExists) coroutineScope.launch { repository.setUseUserMlModel(it) }
                                else Toast.makeText(context, "Upload a model first", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    
                    if (customFeatureCount >= 3) {
                        val pathLocal = userMlPath
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = meanText, onValueChange = { meanText = it }, label = { Text("Mean") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                            OutlinedTextField(value = stdText, onValueChange = { stdText = it }, label = { Text("Std Dev") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        }
                        TextButton(
                            onClick = {
                                val m = meanText.toFloatOrNull()
                                val s = stdText.toFloatOrNull()
                                if (m != null && s != null && pathLocal != null) {
                                    coroutineScope.launch { 
                                        repository.saveUserMlStats(m, s)
                                        repository.backfillCustomPredictions(context, pathLocal, m, s)
                                    }
                                    Toast.makeText(context, "Calibration saved", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("SAVE CALIBRATION") }
                    }

                    Button(
                        onClick = { modelPickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ModelTraining, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (modelExists) "Replace Model" else "Upload Model")
                    }
                }
            }
        }

        // --- 3. DATA & CONTROLS SECTION ---
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    colors = if (isServiceRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) { Text(if (isServiceRunning) "Stop Monitoring" else "Start Monitoring") }

                OutlinedButton(onClick = { csvPickerLauncher.launch("text/*") }) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import CSV")
                }

                OutlinedButton(onClick = {
                    val header = "id,startTimeMillis,endTimeMillis,durationSeconds,category,heuristicCategory,targetBedtimeHour\n"
                    val csvContent = sessions.joinToString(separator = "\n") { session ->
                        "${session.id},${session.startTimeMillis},${session.endTimeMillis},${session.durationSeconds},${session.category},${session.heuristicCategory},${session.targetBedtimeHour}"
                    }
                    saveTextToFile(context, header + csvContent, "sleep_data_${System.currentTimeMillis()}.csv")
                    Toast.makeText(context, "Exported!", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export CSV")
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                val newSettings = tempSettings.copy(baseBedtime = UserTime(hour, minute))
                tempSettings = newSettings
                showTimePicker = false
                coroutineScope.launch { onSettingsChanged(newSettings) }
            },
            initialTime = tempSettings.baseBedtime.toMillis()
        )
    }

    if (showDocDialog) {
        AlertDialog(
            onDismissRequest = { showDocDialog = false },
            confirmButton = { TextButton(onClick = { showDocDialog = false }) { Text("Got it") } },
            title = { Text("Model Specs") },
            text = {
                Text("Outputs: 0:SLEEP, 1:NAP, 2:IDLE\n\nFeatures (In Order):\n1. start_offset\n2. end_offset\n3. duration_z_score\n4. is_weekend\n5. start_time_norm\n6. end_time_norm", fontSize = 13.sp)
            }
        )
    }
}

private suspend fun importCsv(context: android.content.Context, uri: Uri, onAdd: (SleepSession) -> Unit, settings: UserSettings): Boolean = withContext(Dispatchers.IO) {
    try {
        val dateFormat = SimpleDateFormat("MM/dd/yy h:mm a", Locale.US)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val lines = inputStream.bufferedReader().readLines()
            if (lines.size <= 1) return@withContext false
            val header = lines[0].split(",")
            val idIdx = header.indexOf("id")
            val startIdx = header.indexOf("startTimeMillis")
            val durIdx = header.indexOf("durationSeconds")
            val catIdx = header.indexOf("category")
            val heurIdx = header.indexOf("heuristicCategory")
            val targetIdx = header.indexOf("targetBedtimeHour")

            lines.drop(1).forEach { line ->
                val parts = line.split(",")
                if (parts.size >= 5) {
                    val start = try { parts[startIdx].toLong() } catch (_: Exception) { dateFormat.parse(parts[startIdx])?.time ?: 0L }
                    val dur = parts[durIdx].toLong()
                    val target = if (targetIdx != -1) parts[targetIdx].toInt() else 22
                    
                    val rawCat = parts[catIdx].lowercase()
                    val mappedCat = when {
                        rawCat == "true" || rawCat == "sleep" -> SleepSession.CATEGORY_SLEEP
                        rawCat == "nap" -> SleepSession.CATEGORY_NAP
                        rawCat == "idle" -> SleepSession.CATEGORY_IDLE
                        else -> HeuristicClassifier(settings).classify(start, dur, target)
                    }

                    onAdd(SleepSession(
                        id = if (idIdx != -1) parts[idIdx] else java.util.UUID.randomUUID().toString(),
                        startTimeMillis = start,
                        endTimeMillis = start + (dur * 1000),
                        durationSeconds = dur,
                        category = mappedCat,
                        heuristicCategory = if (heurIdx != -1) parts[heurIdx].uppercase() else mappedCat,
                        targetBedtimeHour = target
                    ))
                }
            }
            true
        } ?: false
    } catch (_: Exception) { false }
}
