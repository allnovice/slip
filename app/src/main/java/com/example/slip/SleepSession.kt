package com.example.slip

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sleep_sessions")
data class SleepSession(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "start_time_millis")
    val startTimeMillis: Long,

    @ColumnInfo(name = "end_time_millis")
    val endTimeMillis: Long,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long,

    @ColumnInfo(name = "is_real_sleep")
    var isRealSleep: Boolean? = null,

    // New: The user's scheduled bedtime hour when this was recorded (0-23)
    @ColumnInfo(name = "target_bedtime_hour")
    val targetBedtimeHour: Int = 22 
)
