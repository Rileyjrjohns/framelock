package dev.framelock.core.testing

import dev.framelock.core.domain.frame.ImageFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch

class FakeImageBufferPoolTest {
    @Test
    fun `acquire returns distinct buffers`() {
        val pool = FakeImageBufferPool()

        val first = pool.acquire("a")
        val second = pool.acquire("a")

        assertNotSame(first, second)
        assertEquals("a", first.label)
        assertEquals(ImageFormat.YUV_420_888, first.format)
        assertTrue(first.planes.isEmpty())
        assertFalse(first.isClosed)
        assertEquals(2, pool.acquiredCount)
    }

    @Test
    fun `close returns the buffer and increments close count`() {
        val pool = FakeImageBufferPool()
        val buffer = pool.acquire("a")

        buffer.close()

        assertTrue(buffer.isClosed)
        assertEquals(1, buffer.closeCount)
        assertEquals(1, pool.closedCount)
        assertEquals(0, pool.outstandingCount)
        assertEquals(0, pool.doubleCloseCount)
        assertDoesNotThrow { pool.assertAllReleased() }
    }

    @Test
    fun `double close is recorded and fails assertAllReleased`() {
        val pool = FakeImageBufferPool()
        val buffer = pool.acquire("s1/f7")

        buffer.close()
        buffer.close()

        assertEquals(2, buffer.closeCount)
        assertEquals(1, pool.closedCount)
        assertEquals(1, pool.doubleCloseCount)
        assertEquals(0, pool.outstandingCount)
        val error = assertThrows<AssertionError> { pool.assertAllReleased() }
        assertTrue("s1/f7" in error.message.orEmpty(), error.message)
        assertTrue("closed more than once" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `outstanding count tracks unreleased buffers`() {
        val pool = FakeImageBufferPool()
        val a = pool.acquire("a")
        val b = pool.acquire("b")
        val c = pool.acquire("c")
        assertEquals(3, pool.outstandingCount)

        b.close()
        assertEquals(2, pool.outstandingCount)

        a.close()
        c.close()
        assertEquals(0, pool.outstandingCount)
        assertEquals(3, pool.acquiredCount)
        assertEquals(3, pool.closedCount)
    }

    @Test
    fun `assertAllReleased names the leaked frame labels`() {
        val pool = FakeImageBufferPool()
        pool.acquire("s1/f0").close()
        pool.acquire("s1/f1")
        pool.acquire("s2/f0")

        val error = assertThrows<AssertionError> { pool.assertAllReleased() }

        val message = error.message.orEmpty()
        assertTrue("s1/f1" in message, message)
        assertTrue("s2/f0" in message, message)
        assertFalse("s1/f0" in message, message)
    }

    @Test
    fun `acquire on an exhausted pool fails with the configured error`() {
        val pool = FakeImageBufferPool(capacity = 2)
        val first = pool.acquire("a")
        pool.acquire("b")

        val error = assertThrows<IllegalStateException> { pool.acquire("c") }
        assertEquals("pool exhausted", error.message)
        assertEquals(2, pool.acquiredCount)

        first.close()
        assertDoesNotThrow { pool.acquire("c") }
    }

    @Test
    fun `capacity must be positive`() {
        assertThrows<IllegalArgumentException> { FakeImageBufferPool(capacity = 0) }
    }

    @Test
    fun `concurrent closes are counted exactly`() {
        val buffers = 200
        val pool = FakeImageBufferPool(capacity = buffers + 2)
        val all = List(buffers) { pool.acquire("b$it") }
        val shared = pool.acquire("shared")
        pool.acquire("spare").close()
        val start = CountDownLatch(1)
        val threads =
            List(THREADS) { index ->
                Thread {
                    start.await()
                    // Each thread closes its own slice, and every thread also closes the shared buffer.
                    all.filterIndexed { position, _ -> position % THREADS == index }.forEach { it.close() }
                    shared.close()
                }
            }

        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join(JOIN_TIMEOUT_MILLIS) }

        assertTrue(threads.none { it.isAlive })
        assertEquals(buffers + 2, pool.closedCount)
        assertEquals(THREADS - 1, pool.doubleCloseCount)
        assertEquals(THREADS, shared.closeCount)
        assertEquals(0, pool.outstandingCount)
        assertEquals(buffers + 2, pool.acquiredCount)
    }

    private companion object {
        const val THREADS = 8
        const val JOIN_TIMEOUT_MILLIS = 10_000L
    }
}
