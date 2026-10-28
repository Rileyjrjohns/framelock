package dev.framelock.live.ui

import java.nio.ByteBuffer

/**
 * Bulk reads of one plane row into a caller-owned array. `ByteBuffer.get(index, dst, ...)` needs API 34, so this reads
 * through a duplicate (own position, same bytes) kept per source buffer: the pool recycles a handful of buffers, so
 * after the first lap no duplicate is created. The source buffer's own position and limit are never touched.
 */
internal class RowReader {
    private val origins = arrayOfNulls<ByteBuffer>(CACHE_SIZE)
    private val views = arrayOfNulls<ByteBuffer>(CACHE_SIZE)
    private var replace = 0

    fun read(
        buffer: ByteBuffer,
        offset: Int,
        length: Int,
        into: ByteArray,
    ) {
        val view = viewOf(buffer)
        view.clear()
        view.position(offset)
        view.get(into, 0, length)
    }

    private fun viewOf(buffer: ByteBuffer): ByteBuffer {
        for (i in 0 until CACHE_SIZE) if (origins[i] === buffer) return views[i]!!
        val view = buffer.duplicate()
        origins[replace] = buffer
        views[replace] = view
        replace = (replace + 1) % CACHE_SIZE
        return view
    }

    private companion object {
        const val CACHE_SIZE = 8
    }
}
