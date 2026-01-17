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

    /**
     * The CURRENT category (The "Actual Truth"). 
     * This changes if the user manually edits the session.
     */
    @ColumnInfo(name = "category")
    var category: String = "IDLE",

    /**
     * THE ORIGINAL GUESS.
     * This stores what the Heuristic rules predicted at the moment the session was created.
     * It NEVER changes, providing a fixed baseline for the Model Lab.
     */
    @ColumnInfo(name = "heuristic_category")
    val heuristicCategory: String = "IDLE",

    @ColumnInfo(name = "target_bedtime_hour")
    val targetBedtimeHour: Int = 22
) {
    companion object {
        const val CATEGORY_SLEEP = "SLEEP"
        const val CATEGORY_NAP = "NAP"
        const val CATEGORY_IDLE = "IDLE"
    }
}
