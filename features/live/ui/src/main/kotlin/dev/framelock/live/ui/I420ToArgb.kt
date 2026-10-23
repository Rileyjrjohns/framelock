package dev.framelock.live.ui

import dev.framelock.live.domain.frame.Plane

/**
 * YUV 4:2:0 to opaque ARGB pixels into a caller-owned array, for the bitmap the screen draws (ADR-010: the renderer
 * copies the analysed frame). BT.601 full range, integer arithmetic, no allocation (ADR-012). Moving this to the GPU
 * is a performance candidate for M7.
 */
internal object I420ToArgb {
    private const val CHANNEL_MAX = 255
    private const val CHROMA_OFFSET = 128
    private const val OPAQUE = 0xFF shl 24
    private const val SHIFT = 10
    private const val R_V = 1436
    private const val G_U = 352
    private const val G_V = 731
    private const val B_U = 1815
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    fun convert(
        planes: List<Plane>,
        width: Int,
        height: Int,
        out: IntArray,
    ) {
        val (y, u, v) = planes
        for (row in 0 until height) {
            for (column in 0 until width) {
                val luma = y.byteAt(row, column)
                val cb = u.byteAt(row / 2, column / 2) - CHROMA_OFFSET
                val cr = v.byteAt(row / 2, column / 2) - CHROMA_OFFSET
                val r = clamp(luma + (R_V * cr shr SHIFT))
                val g = clamp(luma - ((G_U * cb + G_V * cr) shr SHIFT))
                val b = clamp(luma + (B_U * cb shr SHIFT))
                out[row * width + column] = OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
            }
        }
    }

    private fun Plane.byteAt(
        row: Int,
        column: Int,
    ): Int = buffer.get(row * rowStride + column * pixelStride).toInt() and CHANNEL_MAX

    private fun clamp(value: Int): Int = value.coerceIn(0, CHANNEL_MAX)
}
