package dev.framelock.live.ui

import dev.framelock.live.domain.frame.Plane

/**
 * RGBA_8888 (bytes R, G, B, X per pixel; the fourth byte is meaningless) to opaque ARGB pixels into a caller-owned
 * array, for the bitmap the screen draws (ADR-010). Alpha is forced to 0xFF here rather than relying on the bitmap's
 * alpha flag. Honours the plane's row and pixel strides, no allocation (ADR-012).
 */
internal object RgbaToArgb {
    private const val CHANNEL_MAX = 255
    private const val OPAQUE = 0xFF shl 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    fun convert(
        plane: Plane,
        width: Int,
        height: Int,
        out: IntArray,
    ) {
        val source = plane.buffer
        for (row in 0 until height) {
            val rowStart = row * plane.rowStride
            for (column in 0 until width) {
                val offset = rowStart + column * plane.pixelStride
                val r = source.get(offset).toInt() and CHANNEL_MAX
                val g = source.get(offset + 1).toInt() and CHANNEL_MAX
                val b = source.get(offset + 2).toInt() and CHANNEL_MAX
                out[row * width + column] = OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
            }
        }
    }
}
