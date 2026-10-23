package dev.framelock.testing

import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.frame.TimestampSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TestFramesTest {
    @Test
    fun `frame takes its buffer from the pool and labels it with session and id`() {
        val pool = FakeImageBufferPool()

        val frame = TestFrames.frame(pool, id = 7, session = 3)

        assertEquals(FrameId(7), frame.id)
        assertEquals(SessionId(3), frame.sessionId)
        assertEquals(1, pool.outstandingCount)
        assertTrue(frame.image is FakeImageBuffer)
        assertEquals("s3/f7", (frame.image as FakeImageBuffer).label)
        frame.image.close()
        pool.assertAllReleased()
    }

    @Test
    fun `frame defaults describe a 30 fps 640 by 480 stream`() {
        val pool = FakeImageBufferPool()

        val frame = TestFrames.frame(pool, id = 3)

        assertEquals(SessionId.FIRST, frame.sessionId)
        assertEquals(3 * 33_000_000L, frame.captureTimestampNanos)
        assertEquals(640, frame.width)
        assertEquals(480, frame.height)
        assertEquals("s1/f3", (frame.image as FakeImageBuffer).label)
    }

    @Test
    fun `explicit capture time and size are used`() {
        val pool = FakeImageBufferPool()

        val frame = TestFrames.frame(pool, id = 0, captureNanos = 99, width = 32, height = 16)

        assertEquals(99L, frame.captureTimestampNanos)
        assertEquals(32, frame.width)
        assertEquals(16, frame.height)
    }

    @Test
    fun `an invalid frame does not leak its buffer`() {
        val pool = FakeImageBufferPool()

        assertThrows<IllegalArgumentException> { TestFrames.frame(pool, id = 0, width = 0) }

        pool.assertAllReleased()
    }

    @Test
    fun `spec is a realtime 640 by 480 YUV stream`() {
        assertEquals(640, TestFrames.SPEC.width)
        assertEquals(480, TestFrames.SPEC.height)
        assertEquals(ImageFormat.YUV_420_888, TestFrames.SPEC.format)
        assertEquals(TimestampSource.REALTIME, TestFrames.SPEC.timestampSource)
    }
}
