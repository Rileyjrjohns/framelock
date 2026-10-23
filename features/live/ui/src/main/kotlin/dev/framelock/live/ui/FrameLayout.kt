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

/**
 * Letterboxes a [frameWidth] x [frameHeight] image into an [areaWidth] x [areaHeight] area, keeping its aspect ratio
 * and centring it. The frame and its detections go through this same fit, so a box always sits on what it describes
 * (ADR-001).
 */
internal fun fitInto(
    areaWidth: Float,
    areaHeight: Float,
    frameWidth: Float,
    frameHeight: Float,
): Fit {
    val scale = minOf(areaWidth / frameWidth, areaHeight / frameHeight)
    val width = frameWidth * scale
    val height = frameHeight * scale
    return Fit((areaWidth - width) / 2, (areaHeight - height) / 2, width, height)
}

/** Top-left corner of a label chip, in pixels. */
internal data class ChipPosition(
    val left: Float,
    val top: Float,
)

/**
 * Places a [chipWidth] x [chipHeight] label chip at the top-left corner of a box, inside [frame]. It sits on the box's
 * top edge; if that leaves the frame it moves inside the box, and if the box is shorter than the chip, below the box.
 * Horizontally it stays within the frame.
 */
internal fun placeChip(
    boxLeft: Float,
    boxTop: Float,
    boxBottom: Float,
    chipWidth: Float,
    chipHeight: Float,
    frame: Fit,
): ChipPosition {
    val left = boxLeft.coerceIn(frame.left, maxOf(frame.left, frame.right - chipWidth))
    val above = boxTop - chipHeight
    val top =
        when {
            above >= frame.top -> above
            boxBottom - boxTop >= chipHeight -> boxTop
            boxBottom + chipHeight <= frame.bottom -> boxBottom
            else -> boxTop.coerceIn(frame.top, maxOf(frame.top, frame.bottom - chipHeight))
        }
    return ChipPosition(left, top)
}
