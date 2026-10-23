package dev.framelock.live.inference.litert

import dev.framelock.live.domain.frame.Plane
import java.nio.ByteBuffer

/**
 * RGBA_8888 (bytes R, G, B, X per pixel; the fourth byte is ignored) to packed RGB, scaled with nearest-neighbour
 * sampling into a caller-owned buffer: the preprocessing step of a uint8 detection model for sources that deliver
 * RGBA. Same sampling as [YuvToRgb], no allocation (ADR-012). Honours the plane's row and pixel strides.
 */
internal object RgbaToRgb {
    fun scaleInto(
        plane: Plane,
        width: Int,
        height: Int,
        out: ByteBuffer,
        outWidth: Int,
        outHeight: Int,
    ) {
        val source = plane.buffer
        for (row in 0 until outHeight) {
            val rowStart = (row * height / outHeight) * plane.rowStride
            for (column in 0 until outWidth) {
                val offset = rowStart + (column * width / outWidth) * plane.pixelStride
                out.put(source.get(offset))
                out.put(source.get(offset + 1))
                out.put(source.get(offset + 2))
            }
        }
    }
}
