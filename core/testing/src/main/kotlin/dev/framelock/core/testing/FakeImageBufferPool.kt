package dev.framelock.core.testing

import dev.framelock.core.domain.frame.ImageBuffer
import dev.framelock.core.domain.frame.ImageFormat
import dev.framelock.core.domain.frame.Plane
import java.util.concurrent.atomic.AtomicInteger

/**
 * A pool of [FakeImageBuffer]s that counts what is acquired and closed, to check ADR-010: every buffer is closed
 * exactly once. Thread-safe: the pipeline closes buffers from its inference thread and from collectors.
 *
 * Pixels are not modelled: a buffer has no planes. [capacity] bounds the buffers that are out at the same time, like
 * a real pool.
 */
class FakeImageBufferPool(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
    }

    private val lock = Any()
    private val outstanding = LinkedHashSet<FakeImageBuffer>()
    private val doubleClosedLabels = mutableListOf<String>()
    private var acquired = 0
    private var closed = 0

    /** Buffers handed out so far. */
    val acquiredCount: Int get() = synchronized(lock) { acquired }

    /** Buffers closed at least once. A repeated close is counted by [doubleCloseCount] only. */
    val closedCount: Int get() = synchronized(lock) { closed }

    /** Buffers handed out and not closed yet. */
    val outstandingCount: Int get() = synchronized(lock) { outstanding.size }

    /** Closes of a buffer that was already closed. */
    val doubleCloseCount: Int get() = synchronized(lock) { doubleClosedLabels.size }

    /** Labels of the buffers that are not closed yet, in acquisition order. */
    val outstandingLabels: List<String> get() = synchronized(lock) { outstanding.map { it.label } }

    /** @throws IllegalStateException when [capacity] buffers are already out. */
    fun acquire(label: String): FakeImageBuffer =
        synchronized(lock) {
            check(outstanding.size < capacity) { "pool exhausted" }
            FakeImageBuffer(label, this).also {
                outstanding += it
                acquired++
            }
        }

    /** @throws AssertionError naming the buffers that were never closed and those closed more than once. */
    fun assertAllReleased() {
        val problems =
            synchronized(lock) {
                buildList {
                    if (outstanding.isNotEmpty()) {
                        add("not released: ${outstanding.map { it.label }}")
                    }
                    if (doubleClosedLabels.isNotEmpty()) {
                        add("closed more than once: $doubleClosedLabels")
                    }
                }
            }
        if (problems.isNotEmpty()) {
            throw AssertionError("Image buffers " + problems.joinToString("; "))
        }
    }

    internal fun release(buffer: FakeImageBuffer) {
        synchronized(lock) {
            if (buffer.recordClose() == 1) {
                outstanding.remove(buffer)
                closed++
            } else {
                doubleClosedLabels += buffer.label
            }
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 64
    }
}

/** Created by [FakeImageBufferPool.acquire]. Closing it more than once is recorded, not rejected. */
class FakeImageBuffer internal constructor(
    val label: String,
    private val pool: FakeImageBufferPool,
) : ImageBuffer {
    private val closes = AtomicInteger()

    override val format: ImageFormat = ImageFormat.YUV_420_888
    override val planes: List<Plane> = emptyList()

    /** How many times [close] was called. */
    val closeCount: Int get() = closes.get()

    val isClosed: Boolean get() = closeCount > 0

    override fun close() {
        pool.release(this)
    }

    internal fun recordClose(): Int = closes.incrementAndGet()

    override fun toString(): String = "FakeImageBuffer($label)"
}
