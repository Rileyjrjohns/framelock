package dev.framelock.live.inference.litert

import dev.framelock.live.domain.frame.Plane
import java.nio.ByteBuffer

/**
 * RGBA_8888 (bytes R, G, B, X per pixel; the fourth byte is ignored) to packed RGB, scaled with nearest-neighbour
 * sampling into a caller-owned buffer: the preprocessing step of a uint8 detection model for sources that deliver
 * RGBA. Same sampling as [YuvToRgb], no allocation in steady state (ADR-012). Honours the plane's row and pixel
 * strides. The state lives in one [RgbaConverter] per calling thread.
 */
internal object RgbaToRgb {
    private val converters = ThreadLocal.withInitial { RgbaConverter() }

    fun scaleInto(
        plane: Plane,
        width: Int,
        height: Int,
        out: ByteBuffer,
        outWidth: Int,
        outHeight: Int,
    ) = converters.get().scaleInto(plane, width, height, out, outWidth, outHeight)
}

/**
 * The converter behind [RgbaToRgb]: sampling tables rebuilt only when sizes or pixel stride change, one bulk read per
 * needed source row, RGB assembled in a reused array and copied out with one bulk put. One instance per thread.
 */
internal class RgbaConverter {
    private val reader = RowReader()
    private var row = ByteArray(0)
    private var rows = IntArray(0)
    private var offsets = IntArray(0)
    private var rgb = ByteArray(0)
    private val tableKey = LongArray(KEY_SIZE)
    private var rowLength = 0

    fun scaleInto(
        plane: Plane,
        width: Int,
        height: Int,
        out: ByteBuffer,
        outWidth: Int,
        outHeight: Int,
    ) {
        prepare(plane.pixelStride, width, height, outWidth, outHeight)
        val size = outWidth * outHeight * RGB
        if (rgb.size < size) rgb = ByteArray(size)
        var lastRow = -1
        var target = 0
        for (outRow in 0 until outHeight) {
            val sourceRow = rows[outRow]
            if (sourceRow != lastRow) {
                reader.read(plane.buffer, sourceRow * plane.rowStride, rowLength, row)
                lastRow = sourceRow
            }
            for (column in 0 until outWidth) {
                val offset = offsets[column]
                rgb[target] = row[offset]
                rgb[target + 1] = row[offset + 1]
                rgb[target + 2] = row[offset + 2]
                target += RGB
            }
        }
        out.put(rgb, 0, size)
    }

    private fun prepare(
        pixelStride: Int,
        width: Int,
        height: Int,
        outWidth: Int,
        outHeight: Int,
    ) {
        val key = tableKey
        if (key[0] == width.toLong() && key[1] == height.toLong() && key[2] == outWidth.toLong() &&
            key[3] == outHeight.toLong() && key[4] == pixelStride.toLong()
        ) {
            return
        }
        if (rows.size < outHeight) rows = IntArray(outHeight)
        if (offsets.size < outWidth) offsets = IntArray(outWidth)
        for (r in 0 until outHeight) rows[r] = r * height / outHeight
        for (column in 0 until outWidth) offsets[column] = (column * width / outWidth) * pixelStride
        // Offsets grow with the column: the last pixel's third byte bounds the bytes a row read has to cover.
        rowLength = offsets[outWidth - 1] + RGB
        if (row.size < rowLength) row = ByteArray(rowLength)
        key[0] = width.toLong()
        key[1] = height.toLong()
        key[2] = outWidth.toLong()
        key[3] = outHeight.toLong()
        key[4] = pixelStride.toLong()
    }

    private companion object {
        const val RGB = 3
        const val KEY_SIZE = 5
    }
}
