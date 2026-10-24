package dev.framelock.live.ui

import dev.framelock.live.domain.frame.Plane

/**
 * RGBA_8888 (bytes R, G, B, X per pixel; the fourth byte is meaningless) to opaque ARGB pixels into a caller-owned
 * array, for the bitmap the screen draws (ADR-010). Alpha is forced to 0xFF here rather than relying on the bitmap's
 * alpha flag. Honours the plane's row and pixel strides, no allocation in steady state (ADR-012).
 *
 * The output may be smaller than the frame: each output pixel samples one source pixel (nearest neighbour, see
 * [SampleMap]), so the cost is proportional to the output pixels, not the frame's. Source rows are read in bulk into a
 * reused array and column offsets come from a cached table. An instance holds its scratch buffers: one per renderer,
 * main thread only.
 */
internal class RgbaToArgb {
    private val rowMap = SampleMap()
    private val columnMap = SampleMap()
    private val reader = RowReader()
    private var row = ByteArray(0)
    private var offsets = IntArray(0)
    private var offsetsStride = 0
    private var offsetsSource = 0
    private var offsetsWidth = 0

    /** Converts the whole frame at its own size: [out] holds `width * height` pixels. */
    fun convert(
        plane: Plane,
        width: Int,
        height: Int,
        out: IntArray,
    ) = convert(plane, width, height, out, width, height)

    /** Converts a [width] x [height] frame into [outWidth] x [outHeight] pixels (each no larger than the frame's). */
    fun convert(
        plane: Plane,
        width: Int,
        height: Int,
        out: IntArray,
        outWidth: Int,
        outHeight: Int,
    ) {
        val rows = rowMap.indices(height, outHeight)
        val columns = columnMap.indices(width, outWidth)
        if (offsetsStride != plane.pixelStride || offsetsSource != width || offsetsWidth != outWidth ||
            offsets.size < outWidth
        ) {
            if (offsets.size < outWidth) offsets = IntArray(outWidth)
            for (i in 0 until outWidth) offsets[i] = columns[i] * plane.pixelStride
            offsetsStride = plane.pixelStride
            offsetsSource = width
            offsetsWidth = outWidth
        }
        val rowLength = (width - 1) * plane.pixelStride + BYTES_USED
        if (row.size < rowLength) row = ByteArray(rowLength)
        var lastRow = -1
        for (outRow in 0 until outHeight) {
            val sourceRow = rows[outRow]
            if (sourceRow != lastRow) {
                reader.read(plane.buffer, sourceRow * plane.rowStride, rowLength, row)
                lastRow = sourceRow
            }
            val base = outRow * outWidth
            for (column in 0 until outWidth) {
                val offset = offsets[column]
                val r = row[offset].toInt() and CHANNEL_MAX
                val g = row[offset + 1].toInt() and CHANNEL_MAX
                val b = row[offset + 2].toInt() and CHANNEL_MAX
                out[base + column] = OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
            }
        }
    }

    private companion object {
        const val CHANNEL_MAX = 255
        const val BYTES_USED = 3
        const val OPAQUE = 0xFF shl 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
    }
}
