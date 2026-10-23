package dev.framelock.telemetry.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One buffered event. The autoincrement id orders events oldest first and is never reused. */
@Entity(tableName = "telemetry_events")
data class TelemetryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordedAtMillis: Long,
    /** The JSON object of [TelemetryEventEncoder]. */
    val payload: String,
)
