package com.example.slip

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SleepDataRepository private constructor(
    private val sleepSessionDao: SleepSessionDao,
    private val dataStore: DataStore<Preferences>
) {
    val sessions: Flow<List<SleepSession>> = sleepSessionDao.getSessions()

    val userSettings: Flow<UserSettings> = dataStore.data.map { preferences ->
        val bedtime = preferences[PreferencesKeys.BASE_BEDTIME] ?: "22:00"
        val offDaysStr = preferences[PreferencesKeys.OFF_DAYS] ?: "7,1"
        val offDaysSet = offDaysStr.split(",").filter { it.isNotEmpty() }.map { it.toInt() }.toSet()
        UserSettings(UserTime.fromString(bedtime), offDaysSet)
    }

    val statsScrollPosition: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.STATS_SCROLL_POSITION] ?: 0
    }

    // Stores a map of "MetricName:Index" for independent swiping
    val statsPeriods: Flow<Map<String, Int>> = dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.STATS_PERIODS_MAP] ?: ""
        raw.split(",").filter { it.contains(":") }.associate {
            val parts = it.split(":")
            parts[0] to (parts[1].toIntOrNull() ?: 0)
        }
    }

    val useUserMlModel: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USE_USER_ML_MODEL] ?: false
    }
    
    val naiveBayesModelPath: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NAIVE_BAYES_MODEL_PATH]
    }

    val userMlModelPath: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_ML_MODEL_PATH]
    }

    val userMlMeans: Flow<List<Float>> = dataStore.data.map { preferences ->
        val meansStr = preferences[PreferencesKeys.USER_ML_MEANS] ?: ""
        if (meansStr.isEmpty()) emptyList() else meansStr.split(",").map { it.toFloat() }
    }

    val userMlStds: Flow<List<Float>> = dataStore.data.map { preferences ->
        val stdsStr = preferences[PreferencesKeys.USER_ML_STDS] ?: ""
        if (stdsStr.isEmpty()) emptyList() else stdsStr.split(",").map { it.toFloat() }
    }

    val isMonitoringEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_MONITORING_ENABLED] ?: true
    }

    val sleepTargetHours: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SLEEP_TARGET_HOURS] ?: 7
    }

    suspend fun getSessionCount(): Int = sleepSessionDao.getSessionCount()

    suspend fun getDurationStats(): Pair<Float, Float> {
        val mean = sleepSessionDao.getDurationMean()?.toFloat() ?: 4475.4f
        val variance = sleepSessionDao.getDurationVariance()?.toFloat() ?: (6533.6f * 6533.6f)
        val stdDev = kotlin.math.sqrt(variance).coerceAtLeast(1f)
        return Pair(mean, stdDev)
    }

    fun addSleepSession(session: SleepSession) {
        CoroutineScope(Dispatchers.IO).launch { sleepSessionDao.insert(session) }
    }

    fun deleteSession(session: SleepSession) {
        CoroutineScope(Dispatchers.IO).launch { sleepSessionDao.delete(session) }
    }

    fun editSession(session: SleepSession, newStart: Long, newEnd: Long, category: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val updatedSession = session.copy(
                startTimeMillis = newStart,
                endTimeMillis = newEnd,
                durationSeconds = (newEnd - newStart) / 1000,
                category = category
            )
            sleepSessionDao.update(updatedSession)
        }
    }

    fun labelSessionById(id: String, category: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val session = sleepSessionDao.getSessionById(id)
            session?.let { sleepSessionDao.update(it.copy(category = category)) }
        }
    }

    suspend fun saveUserSettings(newSettings: UserSettings) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BASE_BEDTIME] = String.format(java.util.Locale.US, "%02d:%02d", newSettings.baseBedtime.hour, newSettings.baseBedtime.minute)
            preferences[PreferencesKeys.OFF_DAYS] = newSettings.offDays.joinToString(",")
        }
    }

    suspend fun saveStatsScrollPosition(position: Int) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.STATS_SCROLL_POSITION] = position }
    }

    suspend fun saveStatsPeriod(metric: String, index: Int) {
        dataStore.edit { preferences ->
            val currentRaw = preferences[PreferencesKeys.STATS_PERIODS_MAP] ?: ""
            val currentMap = currentRaw.split(",")
                .filter { it.contains(":") }
                .associate {
                    val parts = it.split(":")
                    parts[0] to parts[1]
                }.toMutableMap()
            
            currentMap[metric] = index.toString()
            preferences[PreferencesKeys.STATS_PERIODS_MAP] = currentMap.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    suspend fun setUseUserMlModel(use: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.USE_USER_ML_MODEL] = use }
    }

    suspend fun saveUserMlStats(means: List<Float>, stds: List<Float>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_ML_MEANS] = means.joinToString(",")
            preferences[PreferencesKeys.USER_ML_STDS] = stds.joinToString(",")
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.IS_MONITORING_ENABLED] = enabled }
    }

    suspend fun setSleepTargetHours(hours: Int) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.SLEEP_TARGET_HOURS] = hours }
    }

    suspend fun saveUserMlModel(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val file = File(context.filesDir, "user_model.tflite")
                val outputStream = FileOutputStream(file)
                inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
                val path = file.absolutePath
                dataStore.edit { preferences -> preferences[PreferencesKeys.USER_ML_MODEL_PATH] = path }
                path
            } catch (_: Exception) { null }
        }
    }

    suspend fun generateAndSaveNaiveBayesModel(context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val sessions = sessions.first() // Get current sessions
                if (sessions.isEmpty()) return@withContext null

                val model = NaiveBayesClassifier.train(sessions)
                val json = NaiveBayesClassifier.toJson(model)
                val file = File(context.filesDir, "naive_bayes_model.json")
                file.writeText(json)

                val path = file.absolutePath
                dataStore.edit { preferences ->
                    preferences[PreferencesKeys.NAIVE_BAYES_MODEL_PATH] = path
                }
                path
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun deleteNaiveBayesModel(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "naive_bayes_model.json")
                if (file.exists()) {
                    file.delete()
                }
                dataStore.edit { preferences ->
                    preferences.remove(PreferencesKeys.NAIVE_BAYES_MODEL_PATH)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun backfillCustomPredictions(context: Context, path: String, means: List<Float>, stds: List<Float>) { }

    private object PreferencesKeys {
        val BASE_BEDTIME = stringPreferencesKey("base_bedtime")
        val OFF_DAYS = stringPreferencesKey("off_days")
        val STATS_SCROLL_POSITION = intPreferencesKey("stats_scroll_position")
        val STATS_PERIODS_MAP = stringPreferencesKey("stats_periods_map")
        val USE_USER_ML_MODEL = booleanPreferencesKey("use_user_ml_model")
        val USER_ML_MODEL_PATH = stringPreferencesKey("user_ml_model_path")
        val USER_ML_MEANS = stringPreferencesKey("user_ml_means")
        val USER_ML_STDS = stringPreferencesKey("user_ml_stds")
        val IS_MONITORING_ENABLED = booleanPreferencesKey("is_monitoring_enabled")
        val SLEEP_TARGET_HOURS = intPreferencesKey("sleep_target_hours")
        val NAIVE_BAYES_MODEL_PATH = stringPreferencesKey("naive_bayes_model_path")
    }

    companion object {
        @Volatile private var INSTANCE: SleepDataRepository? = null
        fun getInstance(context: Context): SleepDataRepository {
            return INSTANCE ?: synchronized(this) {
                val database = AppDatabase.getInstance(context)
                val instance = SleepDataRepository(database.sleepSessionDao(), context.dataStore)
                INSTANCE = instance
                instance
            }
        }
    }
}
