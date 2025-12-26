package com.example.slip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelLabScreen(
    sessions: List<SleepSession>,
    repository: SleepDataRepository,
    onNavigateBack: () -> Unit
) {
    val userMlPath by repository.userMlModelPath.collectAsState(initial = null)
    val useUserMl by repository.useUserMlModel.collectAsState(initial = false)
    val userMlMean by repository.userMlMean.collectAsState(initial = 0f)
    val userMlStd by repository.userMlStd.collectAsState(initial = 1f)
    val userSettings by repository.userSettings.collectAsState(initial = UserSettings.default)
    
    val modelExists = remember(userMlPath) { userMlPath?.let { File(it).exists() } ?: false }
    
    // MASTER UI SWITCH: Only show ML if it exists AND is toggled ON
    val showMlData = modelExists && useUserMl

    val evaluationResults = remember(sessions, userMlPath, showMlData, userMlMean, userMlStd, userSettings) {
        val engine = ModelLabEngine(
            settings = userSettings,
            customPath = if (showMlData) userMlPath else null,
            customMean = userMlMean,
            customStd = userMlStd
        )
        
        sessions.map { session ->
            val mlPred = if (showMlData) {
                engine.runAll(session.startTimeMillis, session.durationSeconds, session.targetBedtimeHour)
            } else null
            
            session to mlPred
        }
    }

    val mlAccuracy = if (evaluationResults.isNotEmpty() && showMlData) {
        val matches = evaluationResults.count { it.first.category == it.second }
        "${(matches * 100) / evaluationResults.size}%"
    } else null

    val baselineAccuracy = if (evaluationResults.isNotEmpty()) {
        val matches = evaluationResults.count { it.first.category == it.first.heuristicCategory }
        "${(matches * 100) / evaluationResults.size}%"
    } else "0%"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Performance Lab") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccuracyCard(
                        title = "Baseline",
                        subtitle = "Rule Success",
                        value = baselineAccuracy,
                        isActive = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (showMlData) {
                        AccuracyCard(
                            title = "Custom Model",
                            subtitle = "ML Success",
                            value = mlAccuracy ?: "0%",
                            isActive = true,
                            modifier = Modifier.weight(1f),
                            isPrimary = true
                        )
                    }
                }
            }

            item {
                Text(text = if (showMlData) "Comparison Analysis" else "Baseline Analysis", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                TableHeader(showMl = showMlData)
            }

            items(evaluationResults) { (session, mlPred) ->
                ComparisonRow(session, mlPred)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun AccuracyCard(title: String, subtitle: String, value: String, isActive: Boolean, modifier: Modifier, isPrimary: Boolean = false) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) 
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (isPrimary) MaterialTheme.colorScheme.primary else Color.Unspecified)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 9.sp)
        }
    }
}

@Composable
private fun TableHeader(showMl: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Session", modifier = Modifier.weight(1.2f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("BASE", modifier = Modifier.weight(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (showMl) {
            Text("ML", modifier = Modifier.weight(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Text("TRUTH", modifier = Modifier.weight(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ComparisonRow(session: SleepSession, mlPred: String?) {
    val timeFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.2f)) {
            Text(text = timeFormatter.format(session.startTimeMillis), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(text = "${session.durationSeconds / 3600}h ${ (session.durationSeconds % 3600) / 60}m", fontSize = 8.sp, color = Color.Gray)
        }
        
        CategoryResultIcon(session.heuristicCategory, Modifier.weight(0.8f))
        
        if (mlPred != null) {
            CategoryResultIcon(mlPred, Modifier.weight(0.8f))
        }
        
        CategoryResultIcon(session.category, Modifier.weight(0.8f), isTruth = true)
    }
}

@Composable
private fun CategoryResultIcon(category: String, modifier: Modifier, isTruth: Boolean = false) {
    val (icon, color) = when (category) {
        SleepSession.CATEGORY_SLEEP -> Icons.Default.CheckCircle to Color(0xFF4CAF50) 
        SleepSession.CATEGORY_NAP -> Icons.Default.Psychology to Color(0xFF2196F3)   
        else -> Icons.Default.Cancel to Color(0xFFE91E63)                             
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = category,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
