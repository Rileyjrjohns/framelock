package dev.framelock.live.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameLayoutTest {
    private val delta = 1e-3f

    @Test
    fun `a wide frame in a tall area is letterboxed top and bottom`() {
        val fit = fitInto(areaWidth = 400f, areaHeight = 800f, frameWidth = 1280f, frameHeight = 720f)
        assertEquals(0f, fit.left, delta)
        assertEquals(400f, fit.width, delta)
        assertEquals(225f, fit.height, delta)
        assertEquals((800f - 225f) / 2, fit.top, delta)
    }

    @Test
    fun `a tall frame in a wide area is pillarboxed left and right`() {
        val fit = fitInto(areaWidth = 800f, areaHeight = 400f, frameWidth = 480f, frameHeight = 640f)
        assertEquals(0f, fit.top, delta)
        assertEquals(400f, fit.height, delta)
        assertEquals(300f, fit.width, delta)
        assertEquals(250f, fit.left, delta)
    }

    @Test
    fun `a frame with the aspect ratio of the area fills it`() {
        val fit = fitInto(areaWidth = 640f, areaHeight = 360f, frameWidth = 1280f, frameHeight = 720f)
        assertEquals(Fit(0f, 0f, 640f, 360f), fit)
    }

    private val frame = Fit(left = 100f, top = 200f, width = 400f, height = 300f)

    @Test
    fun `the chip sits on the top edge of the box`() {
        val at =
            placeChip(boxLeft = 150f, boxTop = 300f, boxBottom = 400f, chipWidth = 80f, chipHeight = 24f, frame = frame)
        assertEquals(ChipPosition(150f, 276f), at)
    }

    @Test
    fun `the chip moves inside the box when the box touches the top of the frame`() {
        val at =
            placeChip(boxLeft = 150f, boxTop = 200f, boxBottom = 400f, chipWidth = 80f, chipHeight = 24f, frame = frame)
        assertEquals(ChipPosition(150f, 200f), at)
    }

    @Test
    fun `the chip moves below a box that is shorter than the chip`() {
        val at =
            placeChip(boxLeft = 150f, boxTop = 205f, boxBottom = 215f, chipWidth = 80f, chipHeight = 24f, frame = frame)
        assertEquals(ChipPosition(150f, 215f), at)
    }

    @Test
    fun `the chip stays in the frame when nothing else fits`() {
        val short = Fit(left = 0f, top = 0f, width = 100f, height = 30f)
        val at =
            placeChip(boxLeft = 10f, boxTop = 20f, boxBottom = 22f, chipWidth = 80f, chipHeight = 24f, frame = short)
        assertEquals(ChipPosition(10f, 6f), at)
    }

    @Test
    fun `the chip is pulled back inside the frame horizontally`() {
        val right =
            placeChip(boxLeft = 480f, boxTop = 300f, boxBottom = 400f, chipWidth = 80f, chipHeight = 24f, frame = frame)
        assertEquals(420f, right.left, delta)
        val left =
            placeChip(boxLeft = 40f, boxTop = 300f, boxBottom = 400f, chipWidth = 80f, chipHeight = 24f, frame = frame)
        assertEquals(100f, left.left, delta)
    }

    @Test
    fun `a chip wider than the frame is anchored at its left edge`() {
        val at =
            placeChip(
                boxLeft = 300f,
                boxTop = 300f,
                boxBottom = 400f,
                chipWidth = 600f,
                chipHeight = 24f,
                frame = frame,
            )
        assertEquals(100f, at.left, delta)
        assertTrue(at.top >= frame.top)
    }
}
