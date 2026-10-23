package dev.framelock.live.ui

/**
 * Status colours. They carry meaning, not style, and fill the status chip, which takes [ON_STATE] text: every colour
 * is light enough for dark text to reach WCAG AA ([Contrast.AA]).
 */
internal object StateColors {
    const val IDLE = 0xFF9CA3AF.toInt()
    const val CONNECTING = 0xFF60A5FA.toInt()
    const val RUNNING = 0xFF34D399.toInt()
    const val DEGRADED = 0xFFFBBF24.toInt()
    const val FAULT = 0xFFF87171.toInt()

    /** Text and dot on a status chip. */
    const val ON_STATE = GlassPalette.BACKGROUND

    val all = intArrayOf(IDLE, CONNECTING, RUNNING, DEGRADED, FAULT)
}
