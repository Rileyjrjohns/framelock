package dev.framelock.live.ui

import java.nio.ByteBuffer

/**
 * For each target index along one axis, the source index it samples (nearest neighbour, taken at the centre of the
 * target cell). The table is rebuilt only when the (source, target) pair changes, so steady state does no work and no
 * allocation here, and the per-pixel loops need no division. Target equal to source gives the identity.
 */
internal class SampleMap {
    private var source = 0
    private var target = 0
    private var table = IntArray(0)

    fun indices(
        source: Int,
        target: Int,
    ): IntArray {
        if (source == this.source && target == this.target) return table
        if (table.size < target) table = IntArray(target)
        for (i in 0 until target) {
            table[i] = ((2L * i + 1) * source / (2L * target)).toInt()
        }
        this.source = source
        this.target = target
        return table
    }
}

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
