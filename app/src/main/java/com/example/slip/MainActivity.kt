package com.example.slip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                // Permissions granted, check if we should start monitoring
                autoStartMonitoring()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        autoStartMonitoring() // Attempt auto-start on load
        
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

    fun checkAndRequestPermissions() {
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
                    onEdit = { session, newStart, newEnd, isSleep ->
                        repository.editSession(session, newStart, newEnd, isSleep)
                    },
                    onAdd = { newSession -> repository.addSleepSession(newSession) },
                    onLabel = { session, isRealSleep -> repository.labelSession(session, isRealSleep) },
                    onNavigateToSettings = { navController.navigate(AppRoutes.SETTINGS) },
                    onNavigateToModelLab = { navController.navigate(AppRoutes.MODEL_LAB) }
                )
            }
            composable(AppRoutes.SETTINGS) {
                val settings by repository.userSettings.collectAsState(initial = UserSettings.default)
                val sessions by repository.sessions.collectAsState(initial = emptyList())
                val coroutineScope = rememberCoroutineScope()

                SettingsScreen(
                    settings = settings,
                    sessions = sessions,
                    onSettingsChanged = { newSettings ->
                        coroutineScope.launch {
                            repository.saveUserSettings(newSettings)
                        }
                    },
                    onAddSession = { session ->
                        repository.addSleepSession(session)
                    },
                    navController = navController,
                    repository = repository
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
