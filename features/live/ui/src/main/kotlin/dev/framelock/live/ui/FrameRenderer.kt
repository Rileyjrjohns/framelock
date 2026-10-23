package dev.framelock.live.ui

import android.graphics.Bitmap
import dev.framelock.live.application.pipeline.AnnotatedFrame
import dev.framelock.live.application.pipeline.DisplaySlot

/**
 * Copies the analysed frame into one of two reused bitmaps (ADR-010): the copy happens inside [DisplaySlot.read],
 * and only when the frame in the slot is the one [AnnotatedFrame] describes, so the overlay always matches the
 * pixels under it (ADR-001). Main thread only.
 */
internal class FrameRenderer(
    private val display: DisplaySlot,
) {
    private val bitmaps = arrayOfNulls<Bitmap>(2)
    private var pixels = IntArray(0)
    private var next = 0
    private var shownFrame: AnnotatedFrame? = null
    private var shownBitmap: Bitmap? = null

    /** The bitmap to draw for [annotated], or null if the slot already moved on (the next state will follow). */
    fun bitmapFor(annotated: AnnotatedFrame): Bitmap? {
        if (annotated == shownFrame) return shownBitmap
        val copied =
            display.read { frame ->
                val matches = frame != null && frame.id == annotated.frameId && frame.sessionId == annotated.sessionId
                if (!matches) return@read false
                ensureCapacity(annotated.width, annotated.height)
                I420ToArgb.convert(frame!!.image.planes, annotated.width, annotated.height, pixels)
                true
            }
        if (!copied) return null
        val target = bitmapSized(annotated.width, annotated.height)
        target.setPixels(pixels, 0, annotated.width, 0, 0, annotated.width, annotated.height)
        shownFrame = annotated
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
