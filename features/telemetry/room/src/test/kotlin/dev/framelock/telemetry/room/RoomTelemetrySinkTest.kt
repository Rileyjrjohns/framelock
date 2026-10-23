package dev.framelock.telemetry.room

import dev.framelock.kernel.telemetry.ScreenId
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.kernel.telemetry.UsageTelemetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomTelemetrySinkTest {
    private val buffer = InMemoryTelemetryBuffer()
    private val logs = mutableListOf<String>()
    private var now = 1_000L

    private fun TestScope.sink(
        capacity: Int = 16,
        batch: Int = 4,
    ) = RoomTelemetrySink(buffer, backgroundScope, { now++ }, { logs += it }, capacity, batch)

    @Test
    fun `record returns at once and the writer stores the event with its wall clock time`() =
        runTest(StandardTestDispatcher()) {
            val sink = sink()

            sink.record(UsageTelemetry.ScreenViewed(ScreenId.LIVE))

            assertEquals(0, buffer.count())
            runCurrent()
            assertEquals(listOf("""{"type":"ScreenViewed","atMillis":1000,"screen":"LIVE"}"""), buffer.payloads)
        }

    @Test
    fun `a full queue drops its oldest events and counts them`() =
        runTest(StandardTestDispatcher()) {
            val sink = sink(capacity = 3, batch = 10)

            repeat(10) {
                now = 100L + it
                sink.record(UsageTelemetry.AppSession)
            }
            runCurrent()

            assertEquals(7, sink.droppedOnOverflow)
            assertEquals(
                listOf(107L, 108L, 109L),
                buffer.payloads.map {
                    Regex("""atMillis":(\d+)""").find(it)!!.groupValues[1].toLong()
                },
            )
            assertEquals(true, logs.any { "queue full" in it })
        }

    @Test
    fun `events are written in batches no larger than the batch size`() =
        runTest(StandardTestDispatcher()) {
            val sink = sink(capacity = 16, batch = 4)

            repeat(10) { sink.record(UsageTelemetry.AppSession) }
            runCurrent()

            assertEquals(10, buffer.count())
            assertEquals(0, sink.droppedOnOverflow)
        }

    @Test
    fun `an event no encoder knows is counted and dropped`() =
        runTest(StandardTestDispatcher()) {
            val sink = sink()

            sink.record(object : TelemetryEvent {})
            sink.record(UsageTelemetry.AppSession)
            runCurrent()

            assertEquals(1, sink.droppedUnencodable)
            assertEquals(1, buffer.count())
        }

    @Test
    fun `a failing buffer loses the batch, is logged, and the sink keeps working`() =
        runTest(StandardTestDispatcher()) {
            val sink = sink()
            buffer.appendFailure = IllegalStateException("disk full")

            sink.record(UsageTelemetry.AppSession)
            runCurrent()
            buffer.appendFailure = null
            sink.record(UsageTelemetry.AppSession)
            runCurrent()

            assertEquals(1, sink.droppedOnWriteFailure)
            assertEquals(1, buffer.count())
            assertEquals(true, logs.any { "write failed" in it })
        }
}
