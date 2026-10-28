package dev.framelock.live.inference.litert

import dev.framelock.live.domain.inference.NormalizedRect

internal fun intersectionOverUnion(
    a: NormalizedRect,
    b: NormalizedRect,
): Float {
    val width = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f)
    val height = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
    val intersection = width * height
    val union = a.width * a.height + b.width * b.height - intersection
    return if (union > 0f) intersection / union else 0f
}
