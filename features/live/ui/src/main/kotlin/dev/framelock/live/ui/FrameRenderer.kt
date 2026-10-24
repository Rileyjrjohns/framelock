package dev.framelock.live.ui

import android.graphics.Bitmap
import dev.framelock.kernel.port.TraceSection
import dev.framelock.kernel.port.Tracer
import dev.framelock.kernel.port.trace
import dev.framelock.live.application.pipeline.AnnotatedFrame
import dev.framelock.live.application.pipeline.DisplaySlot
import dev.framelock.live.domain.frame.ImageFormat

/**
 * Copies the analysed frame into one of two reused bitmaps (ADR-010): the copy happens inside [DisplaySlot.read],
 * and only when the frame in the slot is the one [AnnotatedFrame] describes, so the overlay always matches the
 * pixels under it (ADR-001). Main thread only.
 *
 * The bitmap is the same frame at display resolution: no larger than the on-screen rectangle the frame will occupy,
 * never larger than the frame. The cost of a render is therefore the pixels on screen (target pixels), not the
 * frame's. Bitmaps and the pixel array are reallocated only when that size changes (rotation), not per frame.
 *
 * The conversion and the bitmap upload are the `RENDER` trace section (ADR-009); it begins and ends on the main thread.
 */
internal class FrameRenderer(
    private val display: DisplaySlot,
    private val tracer: Tracer,
) {
    private val bitmaps = arrayOfNulls<Bitmap>(2)
    private val i420 = I420ToArgb()
    private val rgba = RgbaToArgb()
    private var pixels = IntArray(0)
    private var next = 0
    private var shownFrame: AnnotatedFrame? = null
    private var shownWidth = 0
    private var shownHeight = 0
    private var shownBitmap: Bitmap? = null

    /**
     * The bitmap to draw for [annotated] on a [displayWidth] x [displayHeight] px rectangle (the fit rectangle), or
     * null if the slot already moved on (the next state will follow). If the display size changed but the slot moved
     * on, the previous bitmap of the same frame is returned: the canvas scales it to the rectangle.
     */
    fun bitmapFor(
        annotated: AnnotatedFrame,
        displayWidth: Int,
        displayHeight: Int,
    ): Bitmap? {
        val width = displayWidth.coerceIn(1, annotated.width)
        val height = displayHeight.coerceIn(1, annotated.height)
        if (annotated == shownFrame && width == shownWidth && height == shownHeight) return shownBitmap
        val rendered = tracer.trace(TraceSection.RENDER) { render(annotated, width, height) }
        return rendered ?: if (annotated == shownFrame) shownBitmap else null
    }

    private fun render(
        annotated: AnnotatedFrame,
        width: Int,
        height: Int,
    ): Bitmap? {
        val copied =
            display.read { frame ->
                val matches = frame != null && frame.id == annotated.frameId && frame.sessionId == annotated.sessionId
                if (!matches) return@read false
                ensureCapacity(width, height)
                val image = frame!!.image
                when (image.format) {
                    ImageFormat.YUV_420_888 -> {
                        i420.convert(image.planes, annotated.width, annotated.height, pixels, width, height)
                    }

                    ImageFormat.RGBA_8888 -> {
                        rgba.convert(image.planes.single(), annotated.width, annotated.height, pixels, width, height)
                    }

                    else -> {
                        return@read false
                    }
                }
                true
            }
        if (!copied) return null
        val target = bitmapSized(width, height)
        target.setPixels(pixels, 0, width, 0, 0, width, height)
        shownFrame = annotated
        shownWidth = width
        shownHeight = height
        shownBitmap = target
        return target
    }

    private fun ensureCapacity(
        width: Int,
        height: Int,
    ) {
        if (pixels.size != width * height) pixels = IntArray(width * height)
    }

    /** Alternates between two bitmaps, so the one being drawn is never the one being filled. */
    private fun bitmapSized(
        width: Int,
        height: Int,
    ): Bitmap {
        next = 1 - next
        val existing = bitmaps[next]
        if (existing != null && existing.width == width && existing.height == height) return existing
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmaps[next] = it }
    }
}
