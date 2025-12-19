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

    @ColumnInfo(name = "target_bedtime_hour")
    val targetBedtimeHour: Int = 22,

    // --- MODEL LAB PREDICTIONS ---
    // We removed pred_dumb as is_real_sleep serves as the baseline truth (Rules + Edits)
    
    @ColumnInfo(name = "pred_default_ml")
    val predDefaultMl: Boolean = false,

    @ColumnInfo(name = "pred_custom_ml")
    val predCustomMl: Boolean = false
)
