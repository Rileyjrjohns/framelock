package dev.framelock.designsystem

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppGlassContrastTest {
    @Test
    fun `both app backdrop endpoints keep primary and secondary text at AA`() {
        for (background in listOf(GlassPalette.BACKGROUND, GlassPalette.INDIGO)) {
            val fill = Contrast.over(GlassPalette.TINT, background)
            for (text in listOf(GlassPalette.TEXT_PRIMARY, GlassPalette.TEXT_SECONDARY)) {
                assertTrue(Contrast.ratio(Contrast.over(text, fill), fill) >= Contrast.AA)
            }
            val danger = Contrast.over(GlassPalette.DANGER_FILL, fill)
            assertTrue(Contrast.ratio(Contrast.over(GlassPalette.TEXT_PRIMARY, danger), danger) >= Contrast.AA)
        }
    }
}
