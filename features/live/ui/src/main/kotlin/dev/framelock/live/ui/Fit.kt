package dev.framelock.live.ui

/** Where the analysed frame lands inside the drawing area, in pixels. */
internal data class Fit(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}
