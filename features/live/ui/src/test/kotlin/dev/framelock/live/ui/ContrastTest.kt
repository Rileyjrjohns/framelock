package dev.framelock.live.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContrastTest {
    @Test
    fun `black on white is the maximum ratio of 21`() {
        assertEquals(21.0, Contrast.ratio(Contrast.BLACK, Contrast.WHITE), 1e-9)
    }

    @Test
    fun `a colour against itself has a ratio of 1`() {
        assertEquals(1.0, Contrast.ratio(0xFF1B5E20.toInt(), 0xFF1B5E20.toInt()), 1e-9)
    }

    @Test
    fun `the alpha channel is ignored`() {
        assertEquals(Contrast.relativeLuminance(0xFFFF4081.toInt()), Contrast.relativeLuminance(0x00FF4081), 0.0)
    }

    @Test
    fun `text is black on a bright background and white on a dark one`() {
        assertEquals(Contrast.BLACK, Contrast.textOn(0xFFFFD600.toInt()))
        assertEquals(Contrast.WHITE, Contrast.textOn(0xFF1A4F8B.toInt()))
    }

    @Test
    fun `every status colour reaches AA with the dark chip text`() {
        StateColors.all.forEach { color ->
            val ratio = Contrast.ratio(color, StateColors.ON_STATE)
            assertTrue(ratio >= Contrast.AA, "0x${color.toUInt().toString(16)} has ratio $ratio with dark text")
        }
    }

    @Test
    fun `a translucent colour painted over an opaque one is mixed by its alpha`() {
        assertEquals(0xFF808080.toInt(), Contrast.over(0x80FFFFFF.toInt(), 0xFF000000.toInt()))
        assertEquals(0xFF123456.toInt(), Contrast.over(0x00FFFFFF, 0xFF123456.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), Contrast.over(0xFFFFFFFF.toInt(), 0xFF000000.toInt()))
    }
}
