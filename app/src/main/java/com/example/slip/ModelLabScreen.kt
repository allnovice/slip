package com.example.slip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val hasCustomModel = userMlPath != null

    val labeledSessions = sessions.filter { it.isRealSleep != null }
    val totalSessions = labeledSessions.size

    fun calculateAccuracy(predicate: (SleepSession) -> Boolean): Int {
        if (totalSessions == 0) return 0
        val matches = labeledSessions.count { predicate(it) == it.isRealSleep }
        return (matches * 100) / totalSessions
    }

    val defaultMlAccuracy = calculateAccuracy { it.predDefaultMl }
    val customMlAccuracy = calculateAccuracy { it.predCustomMl }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Experiment Lab") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("ML Performance Accuracy", style = MaterialTheme.typography.titleMedium)
                Text(
                    "How often AI models match the final sleep labels (Rules + Your edits).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccuracyCard("System ML", "$defaultMlAccuracy%", Modifier.weight(1f))
                    AccuracyCard(
                        "Custom ML", 
                        if (hasCustomModel) "$customMlAccuracy%" else "N/A", 
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                Text("Prediction Log", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                TableHeader(hasCustomModel)
            }

            items(sessions) { session ->
                ComparisonRow(session, hasCustomModel)
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun AccuracyCard(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TableHeader(hasCustomModel: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Time", modifier = Modifier.weight(1.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("System AI", modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (hasCustomModel) {
            Text("Custom AI", modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Text("FINAL TRUTH", modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ComparisonRow(session: SleepSession, hasCustomModel: Boolean) {
    val timeFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = timeFormatter.format(session.startTimeMillis),
            modifier = Modifier.weight(1.5f),
            fontSize = 10.sp
        )
        PredictionIcon(session.predDefaultMl, Modifier.weight(1f))
        if (hasCustomModel) {
            PredictionIcon(session.predCustomMl, Modifier.weight(1f))
        }
        PredictionIcon(session.isRealSleep ?: false, Modifier.weight(1f), isTruth = true)
    }
}

@Composable
private fun PredictionIcon(value: Boolean, modifier: Modifier, isTruth: Boolean = false) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = if (value) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = when {
                isTruth -> MaterialTheme.colorScheme.primary
                value -> Color(0xFF4CAF50)
                else -> Color(0xFFE91E63)
            },
            modifier = Modifier.size(if (isTruth) 20.dp else 18.dp)
        )
    }
}
