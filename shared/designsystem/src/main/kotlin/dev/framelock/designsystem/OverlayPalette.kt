package dev.framelock.designsystem

/**
 * Colours for detection boxes: one per class, fixed, so a class keeps its colour from frame to frame and from run to
 * run. They are bright and saturated to stay visible on both dark and bright video; the box is also drawn over a dark
 * outline, and the label chip takes black or white text by contrast ([Contrast.textOn]).
 */
object OverlayPalette {
    private val colors =
        intArrayOf(
            0xFFFFD600.toInt(), // yellow
            0xFF00E5FF.toInt(), // cyan
            0xFF76FF03.toInt(), // lime
            0xFFFF4081.toInt(), // pink
            0xFFB388FF.toInt(), // lavender
            0xFFFF9100.toInt(), // orange
            0xFF1DE9B6.toInt(), // mint
            0xFF448AFF.toInt(), // blue
        )

    val size: Int get() = colors.size

    /** The colour of class [label]; the same class always gets the same colour. */
    fun colorOf(label: Int): Int = colors[Math.floorMod(label, colors.size)]
}
