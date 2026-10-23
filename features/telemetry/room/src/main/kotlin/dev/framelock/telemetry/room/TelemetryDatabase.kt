package dev.framelock.telemetry.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TelemetryEventEntity::class], version = 1, exportSchema = true)
abstract class TelemetryDatabase : RoomDatabase() {
    abstract fun dao(): TelemetryDao

    companion object {
        const val FILE_NAME = "telemetry.db"

        fun open(context: Context): TelemetryDatabase =
            Room.databaseBuilder(context.applicationContext, TelemetryDatabase::class.java, FILE_NAME).build()
    }
}
