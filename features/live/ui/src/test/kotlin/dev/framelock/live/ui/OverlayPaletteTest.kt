package dev.framelock.live.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverlayPaletteTest {
    private val colors = (0 until OverlayPalette.size).map { OverlayPalette.colorOf(it) }

    @Test
    fun `a class always gets the same colour`() {
        assertEquals(OverlayPalette.colorOf(17), OverlayPalette.colorOf(17))
    }

    @Test
    fun `classes wrap around the palette`() {
        assertEquals(OverlayPalette.colorOf(3), OverlayPalette.colorOf(3 + OverlayPalette.size))
        assertEquals(OverlayPalette.colorOf(0), OverlayPalette.colorOf(OverlayPalette.size * 10))
    }

    @Test
    fun `the colours of the palette are distinct and opaque`() {
        assertEquals(OverlayPalette.size, colors.toSet().size)
        colors.forEach { assertEquals(0xFF, it ushr 24) }
    }

    @Test
    fun `a label chip is readable on every palette colour`() {
        colors.forEach { color ->
            val ratio = Contrast.ratio(color, Contrast.textOn(color))
            assertTrue(ratio >= Contrast.AA, "0x${color.toUInt().toString(16)} has ratio $ratio with its chip text")
        }
    }
}
