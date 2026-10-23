package dev.framelock.live.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlassPaletteTest {
    private val picture = GlassPalette.WORST_CASE_PICTURE
    private val blurredPanel = Contrast.over(GlassPalette.TINT, Contrast.over(GlassPalette.VIDEO_SHADE, picture))
    private val flatPanel = Contrast.over(GlassPalette.FLAT_FILL, picture)

    private fun assertAa(
        text: Int,
        panel: Int,
        what: String,
    ) {
        val ratio = Contrast.ratio(Contrast.over(text, panel), panel)
        assertTrue(ratio >= Contrast.AA, "$what has ratio $ratio")
    }

    @Test
    fun `primary and secondary text reach AA on a blurred panel over a white picture`() {
        assertAa(GlassPalette.TEXT_PRIMARY, blurredPanel, "primary text")
        assertAa(GlassPalette.TEXT_SECONDARY, blurredPanel, "secondary text")
    }

    @Test
    fun `primary and secondary text reach AA on the flat fallback panel over a white picture`() {
        assertAa(GlassPalette.TEXT_PRIMARY, flatPanel, "primary text")
        assertAa(GlassPalette.TEXT_SECONDARY, flatPanel, "secondary text")
    }

    @Test
    fun `the flat fill equals the shade then the tint`() {
        val sequential = Contrast.over(GlassPalette.TINT, Contrast.over(0xCC000000.toInt(), picture))
        assertEquals(Contrast.relativeLuminance(sequential), Contrast.relativeLuminance(flatPanel), 0.01)
    }

    @Test
    fun `white text on the red Stop button reaches AA over a white picture`() {
        val stop = Contrast.over(GlassPalette.DANGER_FILL, blurredPanel)
        assertAa(GlassPalette.TEXT_PRIMARY, stop, "Stop label")
    }

    @Test
    fun `dark text reaches AA on both ends and the middle of the accent gradient`() {
        // Half of the end colour painted over the start colour.
        val middle =
            Contrast.over(
                0x80000000.toInt() or (GlassPalette.ACCENT_END and 0xFFFFFF),
                GlassPalette.ACCENT_START,
            )
        listOf(GlassPalette.ACCENT_START, GlassPalette.ACCENT_END, middle).forEach { fill ->
            val ratio = Contrast.ratio(fill, GlassPalette.ON_ACCENT)
            assertTrue(ratio >= Contrast.AA, "0x${fill.toUInt().toString(16)} has ratio $ratio with dark text")
        }
    }

    @Test
    fun `white text reaches AA on the detection label chip over a white picture`() {
        val chip = Contrast.over(GlassPalette.CHIP_FILL, picture)
        assertTrue(Contrast.ratio(chip, Contrast.WHITE) >= Contrast.AA)
    }
}
