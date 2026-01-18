package com.example.slip

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.slip.ui.theme.SlipTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object AppRoutes {
    const val SLEEP_LIST = "sleep_list"
    const val SETTINGS = "settings"
    const val MODEL_LAB = "model_lab"
}

class MainActivity : ComponentActivity() {

    private val repository by lazy { SleepDataRepository.getInstance(this) }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // Permissions handled, attempt auto-start
            autoStartMonitoring()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        autoStartMonitoring()
        
        setContent {
            SlipTheme {
                AppMainScreen(repository = repository)
            }
        }
    }

    private fun autoStartMonitoring() {
        lifecycleScope.launch {
            val isEnabled = repository.isMonitoringEnabled.first()
            if (isEnabled) {
                val serviceIntent = Intent(this@MainActivity, ScreenMonitorService::class.java)
                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMainScreen(repository: SleepDataRepository) {
    val navController = rememberNavController()

    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.SLEEP_LIST,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppRoutes.SLEEP_LIST) {
                val sessions by repository.sessions.collectAsState(initial = emptyList())
                SleepSessionList(
                    sessions = sessions,
                    repository = repository,
                    onDelete = { session -> repository.deleteSession(session) },
                    onEdit = { session, newStart, newEnd, category ->
                        repository.editSession(session, newStart, newEnd, category)
                    },
                    onAdd = { newSession -> repository.addSleepSession(newSession) },
                    onLabel = { session, category -> 
                        repository.labelSessionById(session.id, category) 
                    },
                    onNavigateToSettings = { navController.navigate(AppRoutes.SETTINGS) },
                    onNavigateToModelLab = { navController.navigate(AppRoutes.MODEL_LAB) }
                )
            }
            composable(AppRoutes.SETTINGS) {
                val settings by repository.userSettings.collectAsState(initial = UserSettings.default)
                val sessions by repository.sessions.collectAsState(initial = emptyList())
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                var isGenerating by remember { mutableStateOf(false) }

                SettingsScreen(
                    settings = settings,
                    sessions = sessions,
                    onSettingsChanged = { newSettings ->
                        scope.launch {
                            repository.saveUserSettings(newSettings)
                        }
                    },
                    onAddSession = { session ->
                        repository.addSleepSession(session)
                    },
                    repository = repository,
                    onGenerateNaiveBayesModel = {
                        if (sessions.isNotEmpty()) {
                            isGenerating = true
                            scope.launch {
                                val path = repository.generateAndSaveNaiveBayesModel(context)
                                isGenerating = false
                                if (path != null) {
                                    Toast.makeText(context, "Naive Bayes model generated!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to generate model.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Not enough data to generate a model.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isGeneratingNaiveBayesModel = isGenerating,
                    onDeleteNaiveBayesModel = {
                        scope.launch {
                            repository.deleteNaiveBayesModel(context)
                            Toast.makeText(context, "Naive Bayes model deactivated.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            composable(AppRoutes.MODEL_LAB) {
                val sessions by repository.sessions.collectAsState(initial = emptyList())
                ModelLabScreen(
                    sessions = sessions,
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
