package dev.framelock.live.ui

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
