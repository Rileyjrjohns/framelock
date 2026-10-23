package dev.framelock.testing

import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.frame.StreamSpec
import dev.framelock.live.domain.frame.TimestampSource
import dev.framelock.live.domain.port.SourceEvent
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FakeFrameSourceTest {
    private val pool = FakeImageBufferPool()
    private val source = FakeFrameSource()

    @Test
    fun `emits scripted events in order`() =
        runTest {
            val spec = StreamSpec(320, 240, ImageFormat.JPEG, TimestampSource.ARRIVAL)
            source.connect(SessionId(2), spec)
            source.emit(TestFrames.frame(pool, id = 0, session = 2))
            source.emit(TestFrames.frame(pool, id = 1, session = 2))
            source.disconnect(DisconnectReason.DEVICE_DETACHED)

            val events = source.events.take(4).toList()

            assertEquals(SourceEvent.Connected(spec, SessionId(2)), events[0])
            assertEquals(FrameId(0), (events[1] as SourceEvent.FrameAvailable).frame.id)
            assertEquals(FrameId(1), (events[2] as SourceEvent.FrameAvailable).frame.id)
            assertEquals(SourceEvent.Disconnected(DisconnectReason.DEVICE_DETACHED), events[3])
            events.filterIsInstance<SourceEvent.FrameAvailable>().forEach { it.frame.image.close() }
            pool.assertAllReleased()
        }

    @Test
    fun `connect uses the default spec`() =
        runTest {
            source.connect(SessionId.FIRST)

            assertEquals(
                SourceEvent.Connected(TestFrames.SPEC, SessionId.FIRST),
                source.events
                    .take(1)
                    .toList()
                    .single(),
            )
        }

    @Test
    fun `scripted open failures then success`() =
        runTest {
            val failure = IllegalStateException("no device")
            val attempts = mutableListOf<Int>()
            source.onOpen = { attempts += it }
            source.failNextOpens(2, failure)

            assertSame(failure, assertThrows<IllegalStateException> { source.open() })
            assertFalse(source.isOpen)
            assertSame(failure, assertThrows<IllegalStateException> { source.open() })
            assertFalse(source.isOpen)
            assertEquals(emptyList<Int>(), attempts)

            source.open()

            assertTrue(source.isOpen)
            assertEquals(3, source.openCalls)
            assertEquals(listOf(3), attempts)
        }

    @Test
    fun `default open failure is an illegal state`() =
        runTest {
            source.failNextOpens(1)

            assertEquals("open failed", assertThrows<IllegalStateException> { source.open() }.message)
        }

    @Test
    fun `onOpen can script events of the new connection`() =
        runTest {
            source.onOpen = { source.connect(SessionId(it.toLong())) }

            source.open()

            assertEquals(
                SourceEvent.Connected(TestFrames.SPEC, SessionId(1)),
                source.events
                    .take(1)
                    .toList()
                    .single(),
            )
        }

    @Test
    fun `records open and close calls`() =
        runTest {
            assertFalse(source.isOpen)
            assertEquals(0, source.openCalls)
            assertEquals(0, source.closeCalls)

            source.open()
            assertTrue(source.isOpen)
            source.close()
            assertFalse(source.isOpen)
            source.open()
            source.close()
            source.close()

            assertEquals(2, source.openCalls)
            assertEquals(3, source.closeCalls)
        }

    @Test
    fun `frames emitted after close are released`() =
        runTest {
            source.open()
            source.close()

            source.emit(TestFrames.frame(pool, id = 0))

            pool.assertAllReleased()
            assertEquals(1, pool.closedCount)
        }

    @Test
    fun `frames emitted again after reopening are delivered`() =
        runTest {
            source.open()
            source.close()
            source.open()

            source.emit(TestFrames.frame(pool, id = 0))

            val event =
                source.events
                    .take(1)
                    .toList()
                    .single() as SourceEvent.FrameAvailable
            assertEquals(0, pool.closedCount)
            event.frame.image.close()
            pool.assertAllReleased()
        }

    @Test
    fun `frames still buffered when the source closes are released`() =
        runTest {
            source.open()
            source.emit(TestFrames.frame(pool, id = 0))
            source.emit(TestFrames.frame(pool, id = 1))
            assertEquals(2, pool.outstandingCount)

            source.close()

            pool.assertAllReleased()
            source.disconnect(DisconnectReason.STREAM_ERROR)
            assertEquals(
                SourceEvent.Disconnected(DisconnectReason.STREAM_ERROR),
                source.events
                    .take(1)
                    .toList()
                    .single(),
            )
        }

    @Test
    fun `undelivered frames are released when the collector is cancelled`() =
        runTest {
            val received = mutableListOf<SourceEvent>()
            val collector = launch { source.events.collect { received += it } }
            runCurrent()

            // The frame is handed to the suspended collector, which is cancelled before it resumes.
            source.emit(TestFrames.frame(pool, id = 0))
            collector.cancel()
            runCurrent()

            assertTrue(received.isEmpty())
            pool.assertAllReleased()
        }
}
