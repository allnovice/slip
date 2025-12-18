package com.example.slip

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {
    @Query("SELECT * FROM sleep_sessions ORDER BY start_time_millis DESC")
    fun getSessions(): Flow<List<SleepSession>>

    @Query("SELECT COUNT(*) FROM sleep_sessions")
    suspend fun getSessionCount(): Int

    // Added queries for dynamic scaling
    @Query("SELECT AVG(duration_seconds) FROM sleep_sessions WHERE is_real_sleep = 1")
    suspend fun getDurationMean(): Double?

    @Query("SELECT AVG(duration_seconds * duration_seconds) - AVG(duration_seconds) * AVG(duration_seconds) FROM sleep_sessions WHERE is_real_sleep = 1")
    suspend fun getDurationVariance(): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SleepSession)

    @Update
    suspend fun update(session: SleepSession)

    @Delete
    suspend fun delete(session: SleepSession)
}

class Converters {
    @TypeConverter
    fun fromBoolean(value: Boolean?): Int? = when (value) {
        true -> 1
        false -> 0
        null -> null
    }

    @TypeConverter
    fun toBoolean(value: Int?): Boolean? = when (value) {
        1 -> true
        0 -> false
        else -> null
    }
}
