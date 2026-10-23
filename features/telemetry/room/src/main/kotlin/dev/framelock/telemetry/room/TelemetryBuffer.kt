package dev.framelock.telemetry.room

import android.content.Context
import java.util.concurrent.atomic.AtomicLong

/** One event waiting for upload. */
class StoredEvent(
    val id: Long,
    val payload: String,
)

/** The capped event store that the sink writes to and the upload run reads from. */
interface TelemetryBuffer {
    /** Appends events as (wall clock millis, JSON object) pairs; events beyond the cap are dropped, oldest first. */
    suspend fun append(payloads: List<Pair<Long, String>>)

    /** At most [limit] events, oldest first. */
    suspend fun oldest(limit: Int): List<StoredEvent>

    suspend fun delete(ids: List<Long>)

    suspend fun count(): Int
}

/**
 * [TelemetryBuffer] over Room. Counts the events the cap dropped (`droppedByCap`) and reports each drop to
 * [onDropped], so a full buffer is visible in the logs.
 */
class RoomTelemetryBuffer(
    private val dao: TelemetryDao,
    private val maxRows: Int = DEFAULT_MAX_ROWS,
    private val onDropped: (Int) -> Unit = {},
) : TelemetryBuffer {
    private val dropped = AtomicLong()

    /** Events dropped by the cap since this buffer was created. */
    val droppedByCap: Long get() = dropped.get()

    init {
        require(maxRows > 0) { "The cap must be positive" }
    }

    override suspend fun append(payloads: List<Pair<Long, String>>) {
        if (payloads.isEmpty()) return
        val rows = payloads.map { (at, json) -> TelemetryEventEntity(recordedAtMillis = at, payload = json) }
        val droppedNow = dao.insertCapped(rows, maxRows)
        if (droppedNow > 0) {
            dropped.addAndGet(droppedNow.toLong())
            onDropped(droppedNow)
        }
    }

    override suspend fun oldest(limit: Int): List<StoredEvent> =
        dao.oldest(limit).map { StoredEvent(it.id, it.payload) }

    override suspend fun delete(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.deleteByIds(ids)
    }

    override suspend fun count(): Int = dao.count()

    companion object {
        const val DEFAULT_MAX_ROWS = 10_000

        /** The buffer over the app's telemetry database; the database file opens on first use. */
        fun open(
            context: Context,
            maxRows: Int = DEFAULT_MAX_ROWS,
            onDropped: (Int) -> Unit = {},
        ): RoomTelemetryBuffer = RoomTelemetryBuffer(TelemetryDatabase.open(context).dao(), maxRows, onDropped)
    }
}
