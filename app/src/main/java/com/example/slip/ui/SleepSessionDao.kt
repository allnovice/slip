package com.example.slip

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {
    @Query("SELECT * FROM sleep_sessions ORDER BY start_time_millis DESC")
    fun getSessions(): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_sessions")
    suspend fun getAllSessionsList(): List<SleepSession>

    @Query("SELECT COUNT(*) FROM sleep_sessions")
    suspend fun getSessionCount(): Int

    @Query("SELECT * FROM sleep_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): SleepSession?

    @Query("SELECT AVG(duration_seconds) FROM sleep_sessions")
    suspend fun getDurationMean(): Double?

    @Query("SELECT AVG(duration_seconds * duration_seconds) - AVG(duration_seconds) * AVG(duration_seconds) FROM sleep_sessions")
    suspend fun getDurationVariance(): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SleepSession)

    @Update
    suspend fun update(session: SleepSession)

    @Delete
    suspend fun delete(session: SleepSession)
}
