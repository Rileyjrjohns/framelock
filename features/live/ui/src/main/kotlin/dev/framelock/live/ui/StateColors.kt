package dev.framelock.live.ui

/** Status colours, chosen for WCAG AA contrast with white text ([Contrast.AA]). They carry meaning, not style. */
internal object StateColors {
    const val IDLE = 0xFF424242.toInt()
    const val CONNECTING = 0xFF1A4F8B.toInt()
    const val RUNNING = 0xFF1B5E20.toInt()
    const val DEGRADED = 0xFF8A4B00.toInt()
    const val FAULT = 0xFFB71C1C.toInt()

    val all = intArrayOf(IDLE, CONNECTING, RUNNING, DEGRADED, FAULT)
}
