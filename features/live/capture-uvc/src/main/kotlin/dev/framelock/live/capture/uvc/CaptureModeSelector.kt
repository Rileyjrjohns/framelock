package dev.framelock.live.capture.uvc

import kotlin.math.abs

/** How a capture card encodes frames on the wire. */
internal enum class CaptureFormat { YUYV, MJPEG }

/** One frame size offered by the device in one [format], with the frame rates it supports at that size. */
internal data class SupportedMode(
    val format: CaptureFormat,
    val width: Int,
    val height: Int,
    val fps: List<Int>,
)

/** The mode to stream in. */
internal data class CaptureMode(
    val format: CaptureFormat,
    val width: Int,
    val height: Int,
    val fps: Int,
)

/**
 * Picks the capture mode from what the device offers. Pure, so the rule is tested without a device.
 *
 * 1. Size: the offered size closest to the target (1280x720 by default) by pixel count; a tie goes to the larger size.
 *    The size is chosen before the format: the model input is small, a larger frame only adds copy and conversion cost.
 * 2. Format at that size:
 *    - YUYV if it reaches [minFps] (no JPEG to decode);
 *    - otherwise MJPEG if it reaches [minFps];
 *    - otherwise MJPEG, then YUYV, at its highest rate.
 *
 *    A format that reaches [minFps] streams at the lowest rate that does: the pipeline conflates frames anyway, so a
 *    faster stream only costs USB bandwidth, JPEG decoding and conversion time.
 *
 * Modes with no frame rate are ignored. Returns null when nothing is usable.
 */
internal class CaptureModeSelector(
    private val targetWidth: Int = DEFAULT_WIDTH,
    private val targetHeight: Int = DEFAULT_HEIGHT,
    private val minFps: Int = DEFAULT_MIN_FPS,
) {
    init {
        require(
            targetWidth > 0 && targetHeight > 0,
        ) { "Target size must be positive, was ${targetWidth}x$targetHeight" }
    }

    fun select(modes: List<SupportedMode>): CaptureMode? {
        val usable = modes.filter { it.fps.isNotEmpty() && it.width > 0 && it.height > 0 }
        val targetArea = targetWidth.toLong() * targetHeight
        val size =
            usable
                .map { it.width to it.height }
                .distinct()
                .minWithOrNull(
                    compareBy<Pair<Int, Int>> { abs(it.first.toLong() * it.second - targetArea) }
                        .thenByDescending { it.first.toLong() * it.second },
                ) ?: return null
        val atSize = usable.filter { it.width == size.first && it.height == size.second }
        return pick(atSize, CaptureFormat.YUYV, lowestReaching = minFps)
            ?: pick(atSize, CaptureFormat.MJPEG, lowestReaching = minFps)
            ?: pick(atSize, CaptureFormat.MJPEG, lowestReaching = 0)
            ?: pick(atSize, CaptureFormat.YUYV, lowestReaching = 0)
    }

    /** [format] at the lowest rate reaching [lowestReaching], or at its highest rate when [lowestReaching] is 0. */
    private fun pick(
        modes: List<SupportedMode>,
        format: CaptureFormat,
        lowestReaching: Int,
    ): CaptureMode? {
        val offered = modes.filter { it.format == format }
        val rates = offered.flatMap { it.fps }
        val fps =
            (if (lowestReaching > 0) rates.filter { it >= lowestReaching }.minOrNull() else rates.maxOrNull())
                ?: return null
        return CaptureMode(format, offered.first().width, offered.first().height, fps)
    }

    private companion object {
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        const val DEFAULT_MIN_FPS = 30
    }
}
