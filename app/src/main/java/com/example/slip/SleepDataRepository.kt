package com.example.slip

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// This part for UserSettings using DataStore is modern and can stay.
// We will keep it alongside the Room database for sessions.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SleepDataRepository private constructor(
    // It now takes a DAO for session management and a DataStore for settings.
    private val sleepSessionDao: SleepSessionDao,
    private val dataStore: DataStore<Preferences>
) {
    // The sessions flow comes DIRECTLY from the database. No more manual loading!
    val sessions: Flow<List<SleepSession>> = sleepSessionDao.getSessions()

    // The UserSettings and SkippedSessionCount logic remains correct.
    val userSettings: Flow<UserSettings> = dataStore.data.map { preferences ->
        val startWeekday = preferences[PreferencesKeys.WEEKDAY_SLEEP_START] ?: "22:00"
        val endWeekday = preferences[PreferencesKeys.WEEKDAY_SLEEP_END] ?: "06:00"
        val startWeekend = preferences[PreferencesKeys.WEEKEND_SLEEP_START] ?: "23:00"
        val endWeekend = preferences[PreferencesKeys.WEEKEND_SLEEP_END] ?: "07:00"
        UserSettings(
            UserTime.fromString(startWeekday),
            UserTime.fromString(endWeekday),
            UserTime.fromString(startWeekend),
            UserTime.fromString(endWeekend)
        )
    }
    private val _skippedSessionCount = MutableStateFlow(0)
    val skippedSessionCount = _skippedSessionCount.asStateFlow()

    // --- All old JSON functions (getAllSessions, etc.) are gone. ---
    // --- The new functions call the DAO on a background thread. ---

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

    fun editSession(session: SleepSession, newStart: Long, newEnd: Long, isSleep: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val updatedSession = session.copy(
                startTimeMillis = newStart,
                endTimeMillis = newEnd,
                durationSeconds = (newEnd - newStart) / 1000,
                isRealSleep = isSleep
            )
            sleepSessionDao.update(updatedSession)
        }
    }

    fun labelSession(session: SleepSession, isRealSleep: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val updatedSession = session.copy(isRealSleep = isRealSleep)
            sleepSessionDao.update(updatedSession)
        }
    }

    fun logSkippedSession() {
        _skippedSessionCount.value++
    }

    suspend fun saveUserSettings(newSettings: UserSettings) {
        dataStore.edit { preferences ->
            // --- THIS IS THE FIX ---
            // Add Locale.US to make the formatting consistent and safe.
            preferences[PreferencesKeys.WEEKDAY_SLEEP_START] = String.format(java.util.Locale.US, "%02d:%02d", newSettings.weekdaySleepStart.hour, newSettings.weekdaySleepStart.minute)
            preferences[PreferencesKeys.WEEKDAY_SLEEP_END] = String.format(java.util.Locale.US, "%02d:%02d", newSettings.weekdaySleepEnd.hour, newSettings.weekdaySleepEnd.minute)
            preferences[PreferencesKeys.WEEKEND_SLEEP_START] = String.format(java.util.Locale.US, "%02d:%02d", newSettings.weekendSleepStart.hour, newSettings.weekendSleepStart.minute)
            preferences[PreferencesKeys.WEEKEND_SLEEP_END] = String.format(java.util.Locale.US, "%02d:%02d", newSettings.weekendSleepEnd.hour, newSettings.weekendSleepEnd.minute)
            // ------------------------------------
        }
    }



    // A private object to hold the keys for DataStore.
    private object PreferencesKeys {
        val WEEKDAY_SLEEP_START = stringPreferencesKey("weekday_sleep_start")
        val WEEKDAY_SLEEP_END = stringPreferencesKey("weekday_sleep_end")
        val WEEKEND_SLEEP_START = stringPreferencesKey("weekend_sleep_start")
        val WEEKEND_SLEEP_END = stringPreferencesKey("weekend_sleep_end")
    }

    // --- The companion object is updated to create the DAO and Database ---
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
