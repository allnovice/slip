package com.example.slip

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
        val startWeekday = preferences[PreferencesKeys.WEEKDAY_SLEEP_START] ?: "22:00"
        val startWeekend = preferences[PreferencesKeys.WEEKEND_SLEEP_START] ?: "23:00"
        UserSettings(
            UserTime.fromString(startWeekday),
            UserTime.fromString(startWeekend)
        )
    }

    val filterDuration: Flow<Float> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FILTER_DURATION] ?: 0f
    }

    val useUserMlModel: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USE_USER_ML_MODEL] ?: false
    }

    val userMlModelPath: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_ML_MODEL_PATH]
    }

    val userMlMean: Flow<Float> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_ML_MEAN] ?: 4475.4f
    }

    val userMlStd: Flow<Float> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_ML_STD] ?: 6533.6f
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
        CoroutineScope(Dispatchers.IO).launch {
            sleepSessionDao.insert(session)
        }
    }

    fun deleteSession(session: SleepSession) {
        CoroutineScope(Dispatchers.IO).launch {
            sleepSessionDao.delete(session)
        }
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

    fun labelSession(session: SleepSession, category: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val updatedSession = session.copy(category = category)
            sleepSessionDao.update(updatedSession)
        }
    }

    suspend fun saveUserSettings(newSettings: UserSettings) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WEEKDAY_SLEEP_START] = String.format(java.util.Locale.US, "%02d:%02d", newSettings.weekdaySleepStart.hour, newSettings.weekdaySleepStart.minute)
            preferences[PreferencesKeys.WEEKEND_SLEEP_START] = String.format(java.util.Locale.US, "%02d:%02d", newSettings.weekendSleepStart.hour, newSettings.weekendSleepStart.minute)
        }
    }

    suspend fun saveFilterDuration(duration: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FILTER_DURATION] = duration
        }
    }

    suspend fun setUseUserMlModel(use: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_USER_ML_MODEL] = use
        }
    }

    suspend fun saveUserMlStats(mean: Float, std: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_ML_MEAN] = mean
            preferences[PreferencesKeys.USER_ML_STD] = std
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_MONITORING_ENABLED] = enabled
        }
    }

    suspend fun setSleepTargetHours(hours: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SLEEP_TARGET_HOURS] = hours
        }
    }

    suspend fun backfillCustomPredictions(context: Context, path: String, mean: Float, std: Float) {
        withContext(Dispatchers.IO) {
            val classifier = UserCustomClassifier(path, mean, std)
            val allSessions = sleepSessionDao.getAllSessionsList()
            allSessions.forEach { session ->
                val newCategory = classifier.classify(session.startTimeMillis, session.durationSeconds, session.targetBedtimeHour)
                if (newCategory != null && newCategory != session.category) {
                    sleepSessionDao.update(session.copy(category = newCategory))
                }
            }
        }
    }

    suspend fun saveUserMlModel(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val file = File(context.filesDir, "user_model.tflite")
                val outputStream = FileOutputStream(file)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                val path = file.absolutePath
                dataStore.edit { preferences ->
                    preferences[PreferencesKeys.USER_ML_MODEL_PATH] = path
                }
                path
            } catch (_: Exception) {
                null
            }
        }
    }

    private object PreferencesKeys {
        val WEEKDAY_SLEEP_START = stringPreferencesKey("weekday_sleep_start")
        val WEEKEND_SLEEP_START = stringPreferencesKey("weekend_sleep_start")
        val FILTER_DURATION = floatPreferencesKey("filter_duration")
        val USE_USER_ML_MODEL = booleanPreferencesKey("use_user_ml_model")
        val USER_ML_MODEL_PATH = stringPreferencesKey("user_ml_model_path")
        val USER_ML_MEAN = floatPreferencesKey("user_ml_mean")
        val USER_ML_STD = floatPreferencesKey("user_ml_std")
        val IS_MONITORING_ENABLED = booleanPreferencesKey("is_monitoring_enabled")
        val SLEEP_TARGET_HOURS = intPreferencesKey("sleep_target_hours")
    }

    companion object {
        @Volatile
        private var INSTANCE: SleepDataRepository? = null

        fun getInstance(context: Context): SleepDataRepository {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    val database = AppDatabase.getInstance(context)
                    instance = SleepDataRepository(database.sleepSessionDao(), context.dataStore)
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}
