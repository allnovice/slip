package com.example.slip

// FIX: Import LocalActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.slip.ui.theme.SlipTheme
import kotlinx.coroutines.launch

object AppRoutes {
    const val SLEEP_LIST = "sleep_list"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {

    private val repository by lazy { SleepDataRepository.getInstance(this) }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                // Permissions have been granted.
            } else {
                // Inform the user that background features might not work correctly.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        setContent {
            SlipTheme {
                AppMainScreen(repository = repository)
            }
        }
    }

    // FIX: Make the function public so it can be called from the composable
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
    val coroutineScope = rememberCoroutineScope()

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
                    onNavigateToSettings = { navController.navigate(AppRoutes.SETTINGS) }
                )
            }
            composable(AppRoutes.SETTINGS) {
                val settings by repository.userSettings.collectAsState(initial = UserSettings.default)
                val sessions by repository.sessions.collectAsState(initial = emptyList())
                // We still need the coroutineScope for the lambda
                val coroutineScope = rememberCoroutineScope()

                SettingsScreen(
                    settings = settings,
                    sessions = sessions,
                    onSettingsChanged = { newSettings ->
                        coroutineScope.launch {
                            repository.saveUserSettings(newSettings)
                        }
                    },
                    navController = navController
                )
            }
        }
    }
}
