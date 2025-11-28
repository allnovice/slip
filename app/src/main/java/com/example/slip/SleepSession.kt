// In app/src/main/java/com/example/slip/SleepSession.kt
package com.example.slip

// --- 1. Add the necessary Room imports ---
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
// -----------------------------------------

import java.util.UUID

// --- 2. Add the @Entity annotation ---
// This tells Room to create a table named "sleep_sessions" for this class.
@Entity(tableName = "sleep_sessions")
data class SleepSession(
    // --- 3. Add the @PrimaryKey annotation ---
    // This marks the 'id' field as the unique identifier for each row.
    @PrimaryKey
    @ColumnInfo(name = "id") // Explicitly name the column
    val id: String = UUID.randomUUID().toString(),
    // ------------------------------------------

    @ColumnInfo(name = "start_time_millis") // Use snake_case for column names (good practice)
    val startTimeMillis: Long,

    @ColumnInfo(name = "end_time_millis")
    val endTimeMillis: Long,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long,

    @ColumnInfo(name = "is_real_sleep")
    var isRealSleep: Boolean? = null // This property holds the user's label
)
