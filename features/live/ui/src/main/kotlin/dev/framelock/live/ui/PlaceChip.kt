package dev.framelock.live.ui

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
