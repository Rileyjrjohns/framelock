package dev.framelock.designsystem

import kotlin.math.pow

/** WCAG 2.x contrast maths on opaque `0xAARRGGBB` colours (the alpha channel is ignored). */
object Contrast {
    /** Minimum ratio for normal-size text (WCAG AA). */
    const val AA = 4.5

    private const val BYTE = 255.0
    private const val LINEAR_THRESHOLD = 0.03928
    private const val LINEAR_DIVISOR = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_SCALE = 1.055
    private const val GAMMA = 2.4
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val FLARE = 0.05

    fun relativeLuminance(argb: Int): Double {
        val red = linear(argb shr RED_SHIFT and BYTE_MASK)
        val green = linear(argb shr GREEN_SHIFT and BYTE_MASK)
        val blue = linear(argb and BYTE_MASK)
        return RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue
    }

    fun ratio(
        first: Int,
        second: Int,
    ): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (maxOf(a, b) + FLARE) / (minOf(a, b) + FLARE)
    }

    /** [top] (with its alpha) painted over the opaque [bottom], as an opaque colour. */
    fun over(
        top: Int,
        bottom: Int,
    ): Int {
        val alpha = (top ushr ALPHA_SHIFT and BYTE_MASK) / BYTE

        fun mix(shift: Int): Int {
            val fore = top shr shift and BYTE_MASK
            val back = bottom shr shift and BYTE_MASK
            return Math.round(fore * alpha + back * (1 - alpha)).toInt()
        }
        return OPAQUE or (mix(RED_SHIFT) shl RED_SHIFT) or (mix(GREEN_SHIFT) shl GREEN_SHIFT) or mix(0)
    }

    /** Black or white, whichever reads better on [background]. */
    fun textOn(background: Int): Int = if (ratio(background, BLACK) >= ratio(background, WHITE)) BLACK else WHITE

    private fun linear(channel: Int): Double {
        val value = channel / BYTE
        return if (value <= LINEAR_THRESHOLD) {
            value / LINEAR_DIVISOR
        } else {
            ((value + GAMMA_OFFSET) / GAMMA_SCALE).pow(GAMMA)
        }
    }

    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val ALPHA_SHIFT = 24
    private const val BYTE_MASK = 0xFF
    private const val OPAQUE = 0xFF000000.toInt()
}
