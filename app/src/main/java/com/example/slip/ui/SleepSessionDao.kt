package com.example.slip

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- 1. The DAO (Data Access Object) ---
// This defines how you interact with your data.
@Dao
interface SleepSessionDao {
    /**
     * Gets all sleep sessions from the table, ordered by start time, newest first.
     * The `Flow` makes this automatically update the UI whenever the data changes.
     */
    @Query("SELECT * FROM sleep_sessions ORDER BY start_time_millis DESC")
    fun getSessions(): Flow<List<SleepSession>>

    /**
     * Inserts a new sleep session into the table.
     * `OnConflictStrategy.REPLACE` means if a session with the same `id` already exists,
     * it will be replaced. This is useful for "upsert" (insert or update) logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SleepSession)

    /**
     * Updates an existing sleep session in the table.
     * Room uses the primary key (`id`) of the passed-in session to find the correct row to update.
     */
    @Update
    suspend fun update(session: SleepSession)

    /**
     * Deletes a sleep session from the table.
     * Room uses the primary key (`id`) to find the row to delete.
     */
    @Delete
    suspend fun delete(session: SleepSession)
}

// --- 2. The Type Converter ---
// Room can't store a nullable Boolean (Boolean?). This class teaches Room
// to convert it to an Integer (1 for true, 0 for false, null for null)
// when saving, and back to a Boolean when reading.
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
// This is the main class that holds the database instance.
@Database(entities = [SleepSession::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sleepSessionDao(): SleepSessionDao

    companion object {
        // @Volatile means that writes to this field are immediately made visible to other threads.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // synchronized means that only one thread can execute this block at a time,
            // which prevents creating multiple database instances.
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
