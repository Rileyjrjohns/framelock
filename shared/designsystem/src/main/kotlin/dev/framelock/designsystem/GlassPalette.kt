package dev.framelock.designsystem

/**
 * The colours of the glass interface as `0xAARRGGBB` integers, so their contrast can be checked on the JVM
 * ([GlassPaletteTest]). A panel is the blurred video, shaded, then tinted; the video behind it is unknown, so the
 * shade is chosen for the worst case, a fully white picture ([Contrast.over]).
 */
object GlassPalette {
    /** App background and the dark text on light fills (status chips, the accent button). */
    const val BACKGROUND = 0xFF0A0C10.toInt()

    /** Centre of the idle background gradient. */
    const val INDIGO = 0xFF1E1B4B.toInt()

    /** White at 95%. */
    const val TEXT_PRIMARY = 0xF2FFFFFF.toInt()

    /** White at 70%: a little above the 65% of the design, because 65% misses AA on a white picture. */
    const val TEXT_SECONDARY = 0xB3FFFFFF.toInt()

    /** Black at 74% over the blurred video: darkens it so white text reads on any picture. */
    const val VIDEO_SHADE = 0xBD000000.toInt()

    /** White at 10%, over the shade (and the fill of buttons inside a panel). */
    const val TINT = 0x1AFFFFFF

    /** White at 14%: the 1 dp edge of every glass shape. */
    const val BORDER = 0x24FFFFFF

    /**
     * The panel without blur (API 29-30, the landscape rail, previews): [VIDEO_SHADE] under [TINT] flattened into one
     * colour, black at 80% then white at 10% (alpha 0.82, grey 31).
     */
    const val FLAT_FILL = 0xD11F1F1F.toInt()

    /** Black at 40%: the panel shadow. */
    const val APP_FLAT_FILL = 0xE6181A22.toInt()

    const val SHADOW = 0x66000000

    /** Red at 22% over a glass button: Stop. */
    const val DANGER_FILL = 0x38F87171
    const val DANGER_BORDER = 0xFFF87171.toInt()

    /** The accent gradient, cyan to violet. Dark text passes AA on the whole gradient. */
    const val ACCENT_START = 0xFF22D3EE.toInt()
    const val ACCENT_END = 0xFF8B5CF6.toInt()
    const val ON_ACCENT = BACKGROUND

    /** Pure white: the picture that makes glass hardest to read. */
    const val WORST_CASE_PICTURE = Contrast.WHITE

    /** Black at 60%: the label chip on a detection box. */
    const val CHIP_FILL = 0x99000000.toInt()
}
