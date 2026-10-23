package dev.framelock.telemetry.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class TelemetryDao {
    @Insert
    abstract suspend fun insertAll(rows: List<TelemetryEventEntity>)

    /** At most [limit] events, oldest first. */
    @Query("SELECT * FROM telemetry_events ORDER BY id ASC LIMIT :limit")
    abstract suspend fun oldest(limit: Int): List<TelemetryEventEntity>

    @Query("DELETE FROM telemetry_events WHERE id IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<Long>): Int

    @Query("SELECT COUNT(*) FROM telemetry_events")
    abstract suspend fun count(): Int

    @Query(
        "DELETE FROM telemetry_events WHERE id IN (SELECT id FROM telemetry_events ORDER BY id ASC LIMIT :count)",
    )
    abstract suspend fun deleteOldest(count: Int): Int

    /**
     * Inserts [rows], then drops the oldest events beyond [maxRows], in one transaction: the table never stays above
     * the cap. Returns how many events were dropped.
     */
    @Transaction
    open suspend fun insertCapped(
        rows: List<TelemetryEventEntity>,
        maxRows: Int,
    ): Int {
        insertAll(rows)
        val excess = count() - maxRows
        return if (excess > 0) deleteOldest(excess) else 0
    }
}
