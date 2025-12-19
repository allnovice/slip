package com.example.slip

import android.content.Context
import androidx.room.*

@Database(entities = [SleepSession::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sleepSessionDao(): SleepSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sleep_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

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
