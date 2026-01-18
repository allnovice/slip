package com.example.slip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    val userMlMeans by repository.userMlMeans.collectAsState(initial = emptyList())
    val userMlStds by repository.userMlStds.collectAsState(initial = emptyList())
    val userSettings by repository.userSettings.collectAsState(initial = UserSettings.default)

    val naiveBayesModelPath by repository.naiveBayesModelPath.collectAsState(initial = null)
    val naiveBayesModel = remember(naiveBayesModelPath) {
        naiveBayesModelPath?.let { path ->
            try {
                val json = File(path).readText()
                NaiveBayesClassifier.fromJson(json)
            } catch (e: Exception) {
                null
            }
        }
    }

    var showNbInfoDialog by remember { mutableStateOf(false) }

    val modelExists = remember(userMlPath) { userMlPath?.let { File(it).exists() } ?: false }

    // MASTER UI SWITCH: Only show ML if it exists AND is toggled ON
    val showMlData = modelExists && useUserMl

    val evaluationResults = remember(sessions, userMlPath, showMlData, userMlMeans, userMlStds, userSettings, naiveBayesModel) {
        val engine = ModelLabEngine(
            customPath = if (showMlData) userMlPath else null,
            customMeans = userMlMeans,
            customStds = userMlStds
        )

        sessions.map { session ->
            val mlPred = if (showMlData) {
                engine.runAll(session.startTimeMillis, session.durationSeconds, session.targetBedtimeHour)
            } else null

            val nbPred = naiveBayesModel?.let { NaiveBayesClassifier.predict(it, session) }

            Triple(session, mlPred, nbPred)
        }
    }

    val mlAccuracy = if (evaluationResults.isNotEmpty() && showMlData) {
        val matches = evaluationResults.count { (session, mlPred, _) -> session.category == mlPred }
        "${(matches * 100) / evaluationResults.size}%"
    } else null

    val nbAccuracy = if (evaluationResults.isNotEmpty() && naiveBayesModel != null) {
        val matches = evaluationResults.count { (session, _, nbPred) -> session.category == nbPred }
        "${(matches * 100) / evaluationResults.size}%"
    } else null

    val baselineAccuracy = if (evaluationResults.isNotEmpty()) {
        val matches = evaluationResults.count { (session, _, _) -> session.category == session.heuristicCategory }
        "${(matches * 100) / evaluationResults.size}%"
    } else "0%"

    if (showNbInfoDialog && naiveBayesModel != null) {
        NaiveBayesInfoDialog(model = naiveBayesModel, onDismiss = { showNbInfoDialog = false })
    }

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                    if (nbAccuracy != null) {
                        AccuracyCard(
                            title = "Naive Bayes",
                            subtitle = "NB Success",
                            value = nbAccuracy,
                            isActive = true,
                            modifier = Modifier.weight(1f),
                            onClick = { showNbInfoDialog = true }
                        )
                    }
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
                Text(text = if (showMlData || nbAccuracy != null) "Comparison Analysis" else "Baseline Analysis", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                TableHeader(showMl = showMlData, showNb = nbAccuracy != null)
            }

            items(evaluationResults) { (session, mlPred, nbPred) ->
                ComparisonRow(session, mlPred, nbPred)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun NaiveBayesInfoDialog(model: NaiveBayesModel, onDismiss: () -> Unit) {
    val featureNames = listOf("start_offset", "duration_z_score", "is_weekend", "is_friday", "is_sunday", "start_hour")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Naive Bayes Model Details") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            LazyColumn {
                val categories = model.classPriors.keys.sorted()
                items(categories) { category ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        val prior = model.classPriors[category] ?: 0.0
                        Text("$category Class", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Prior Probability: ${String.format("%.3f", prior)}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        model.featureParams[category]?.forEachIndexed { index, params ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${featureNames[index]}:", style = MaterialTheme.typography.labelMedium)
                                Text("μ=${String.format("%.2f", params.first)}, σ=${String.format("%.2f", params.second)}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
        }
    )
}

@Composable
private fun AccuracyCard(
    title: String,
    subtitle: String,
    value: String,
    isActive: Boolean,
    modifier: Modifier,
    isPrimary: Boolean = false,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable(enabled = isActive, onClick = onClick),
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
private fun TableHeader(showMl: Boolean, showNb: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Session", modifier = Modifier.weight(1.2f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("BASE", modifier = Modifier.weight(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (showNb) {
            Text("NB", modifier = Modifier.weight(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (showMl) {
            Text("ML", modifier = Modifier.weight(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Text("TRUTH", modifier = Modifier.weight(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ComparisonRow(session: SleepSession, mlPred: String?, nbPred: String?) {
    val timeFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.2f)) {
            Text(text = timeFormatter.format(session.startTimeMillis), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(text = "${session.durationSeconds / 3600}h ${ (session.durationSeconds % 3600) / 60}m", fontSize = 8.sp, color = Color.Gray)
        }

        CategoryResultIcon(session.heuristicCategory, Modifier.weight(0.8f))

        if (nbPred != null) {
            CategoryResultIcon(nbPred, Modifier.weight(0.8f))
        }

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
