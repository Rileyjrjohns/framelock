package dev.framelock.telemetry.room

import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.telemetry.domain.port.TelemetrySink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Writes accepted events to the [TelemetryBuffer] without ever blocking the caller (ADR-004, M6). `record` is called
 * from the main thread and from the inference thread: it only stamps the wall-clock time and hands the event to a
 * bounded queue that drops its oldest element when full. One writer coroutine on [scope] (give it `Dispatchers.IO`)
 * drains the queue in small batches, encodes and appends them.
 *
 * Nothing here throws into the caller: a failed write is counted and logged, the batch is lost.
 */
class RoomTelemetrySink(
    private val buffer: TelemetryBuffer,
    scope: CoroutineScope,
    private val wallClockMillis: () -> Long,
    private val log: (String) -> Unit = {},
    queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val writeBatchSize: Int = DEFAULT_WRITE_BATCH_SIZE,
) : TelemetrySink {
    private val overflow = AtomicLong()
    private val unencodable = AtomicLong()
    private val writeFailures = AtomicLong()

    private class Pending(
        val event: TelemetryEvent,
        val atMillis: Long,
    )

    private val queue =
        Channel<Pending>(queueCapacity, BufferOverflow.DROP_OLDEST) { overflow.incrementAndGet() }

    /** Events dropped because the queue was full (the writer could not keep up). */
    val droppedOnOverflow: Long get() = overflow.get()

    /** Events no encoder knows; the allowlist should have rejected them already. */
    val droppedUnencodable: Long get() = unencodable.get()

    /** Events lost because the buffer write failed. */
    val droppedOnWriteFailure: Long get() = writeFailures.get()

    init {
        require(queueCapacity > 0 && writeBatchSize > 0) { "Queue capacity and write batch size must be positive" }
        scope.launch { writeLoop() }
    }

    override fun record(event: TelemetryEvent) {
        queue.trySend(Pending(event, wallClockMillis()))
    }

    private suspend fun writeLoop() {
        var reportedOverflow = 0L
        while (true) {
            val first = queue.receiveCatching().getOrNull() ?: return
            val batch = ArrayList<Pending>(writeBatchSize)
            batch += first
            while (batch.size < writeBatchSize) {
                batch += queue.tryReceive().getOrNull() ?: break
            }
            write(batch)
            val overflowNow = overflow.get()
            if (overflowNow != reportedOverflow) {
                reportedOverflow = overflowNow
                log("Telemetry queue full: $overflowNow events dropped so far")
            }
        }
    }

    private suspend fun write(batch: List<Pending>) {
        val rows =
            batch.mapNotNull { pending ->
                TelemetryEventEncoder.encode(pending.event, pending.atMillis)?.let { pending.atMillis to it }
                    ?: run {
                        unencodable.incrementAndGet()
                        log("Telemetry event of an unknown type dropped: ${pending.event::class.simpleName}")
                        null
                    }
            }
        try {
            buffer.append(rows)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            writeFailures.addAndGet(rows.size.toLong())
            log("Telemetry write failed, ${rows.size} events lost: ${e::class.simpleName}")
        }
    }

    companion object {
        const val DEFAULT_QUEUE_CAPACITY = 1024
        const val DEFAULT_WRITE_BATCH_SIZE = 64
    }
}
