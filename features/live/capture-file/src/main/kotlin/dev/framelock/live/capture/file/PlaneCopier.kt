package dev.framelock.live.capture.file

import java.nio.ByteBuffer

/**
 * Copies one source plane into a tight destination plane without per-frame allocation (ADR-012). `ByteBuffer.get(index,
 * dst, ...)` needs API 34, so reads go through a duplicate (own position and limit, same bytes) kept per source buffer:
 * the source recycles a handful of buffers, so after the first lap no duplicate is created. The source buffer's own
 * position and limit are never touched, and no byte beyond the last one of the plane is read.
 *
 * - Pixel stride 1 with no row padding: the whole plane is one bulk copy.
 * - Pixel stride 1 with row padding: each row is one bulk copy from the duplicate straight into the destination (direct
 *   to direct, no intermediate array). A repositioned duplicate costs a few field writes, far less than a duplicate per
 *   row.
 * - Pixel stride > 1 (interleaved chroma): each row is read in bulk into a reused array, de-interleaved by a tight array
 *   loop into a reused plane array, then the plane is written to the destination with one bulk put.
 *
 * Not thread-safe: the scratch arrays are shared, and one source thread feeds the pool.
 */
internal class PlaneCopier {
    private val origins = arrayOfNulls<ByteBuffer>(CACHE_SIZE)
    private val views = arrayOfNulls<ByteBuffer>(CACHE_SIZE)
    private var replace = 0
    private var rowScratch = ByteArray(0)
    private var planeScratch = ByteArray(0)

    /** Duplicates currently cached. */
    val cachedViews: Int get() = origins.count { it != null }

    /** Bytes held by the scratch arrays; stable once the first frame has been copied. */
    val scratchBytes: Int get() = rowScratch.size + planeScratch.size

    /** Fills [target] (cleared, then flipped) with [columns] x [rows] bytes read from [source]. */
    fun copy(
        source: SourcePlane,
        target: ByteBuffer,
        columns: Int,
        rows: Int,
    ) {
        target.clear()
        val bytes = source.buffer
        val start = bytes.position()
        val limit = bytes.limit()
        val view = viewOf(bytes)
        if (source.pixelStride == 1 && source.rowStride == columns) {
            view.limit(limit)
            view.position(start)
            view.limit(start + columns * rows)
            target.put(view)
        } else if (source.pixelStride == 1) {
            for (row in 0 until rows) {
                val rowStart = start + row * source.rowStride
                view.limit(limit)
                view.position(rowStart)
                view.limit(rowStart + columns)
                target.put(view)
            }
        } else {
            val rowBytes = (columns - 1) * source.pixelStride + 1
            if (rowScratch.size < rowBytes) rowScratch = ByteArray(rowBytes)
            if (planeScratch.size < columns * rows) planeScratch = ByteArray(columns * rows)
            val stride = source.pixelStride
            for (row in 0 until rows) {
                view.limit(limit)
                view.position(start + row * source.rowStride)
                view.get(rowScratch, 0, rowBytes)
                val out = row * columns
                var index = 0
                for (column in 0 until columns) {
                    planeScratch[out + column] = rowScratch[index]
                    index += stride
                }
            }
            target.put(planeScratch, 0, columns * rows)
        }
        target.flip()
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
