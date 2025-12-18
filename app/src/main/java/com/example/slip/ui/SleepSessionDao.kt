package com.example.slip

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- 1. The DAO (Data Access Object) ---
@Dao
interface SleepSessionDao {
    @Query("SELECT * FROM sleep_sessions ORDER BY start_time_millis DESC")
    fun getSessions(): Flow<List<SleepSession>>

    @Query("SELECT COUNT(*) FROM sleep_sessions")
    suspend fun getSessionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SleepSession)

    @Update
    suspend fun update(session: SleepSession)

    @Delete
    suspend fun delete(session: SleepSession)
}

// --- 2. The Type Converter ---
class Converters {
    @TypeConverter
    fun fromBoolean(value: Boolean?): Int? {
        return when (value) {
            true -> 1
            false -> 0
            null -> null
        }
    }

    @TypeConverter
    fun toBoolean(value: Int?): Boolean? {
        return when (value) {
            1 -> true
            0 -> false
            else -> null
        }
    }
}

// --- 3. The Database Class ---
@Database(entities = [SleepSession::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sleepSessionDao(): SleepSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "sleep_database"
                    ).build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}
