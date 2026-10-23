package dev.framelock.live.inference.litert

import dev.framelock.live.domain.frame.Plane
import java.nio.ByteBuffer

/**
 * YUV 4:2:0 to packed RGB (one byte per channel), scaled with nearest-neighbour sampling into a caller-owned buffer:
 * the preprocessing step of a uint8 detection model. BT.601 full-range coefficients, integer arithmetic, no
 * allocation (ADR-012). Handles any row and pixel strides.
 */
internal object YuvToRgb {
    private const val CHANNEL_MAX = 255
    private const val CHROMA_OFFSET = 128

    // BT.601 coefficients scaled by 2^10.
    private const val SHIFT = 10
    private const val R_V = 1436
    private const val G_U = 352
    private const val G_V = 731
    private const val B_U = 1815

    fun scaleInto(
        planes: List<Plane>,
        width: Int,
        height: Int,
        out: ByteBuffer,
        outWidth: Int,
        outHeight: Int,
    ) {
        val (y, u, v) = planes
        for (row in 0 until outHeight) {
            val sourceRow = row * height / outHeight
            for (column in 0 until outWidth) {
                val sourceColumn = column * width / outWidth
                val luma = y.byteAt(sourceRow, sourceColumn)
                val cb = u.byteAt(sourceRow / 2, sourceColumn / 2) - CHROMA_OFFSET
                val cr = v.byteAt(sourceRow / 2, sourceColumn / 2) - CHROMA_OFFSET
                out.put(clamp(luma + (R_V * cr shr SHIFT)))
                out.put(clamp(luma - ((G_U * cb + G_V * cr) shr SHIFT)))
                out.put(clamp(luma + (B_U * cb shr SHIFT)))
            }
        }
    }

    private fun Plane.byteAt(
        row: Int,
        column: Int,
    ): Int = buffer.get(row * rowStride + column * pixelStride).toInt() and CHANNEL_MAX

    private fun clamp(value: Int): Byte = value.coerceIn(0, CHANNEL_MAX).toByte()
}
