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
 *    - YUYV if it reaches [minYuyvFps] (no JPEG to decode), at the lowest rate that does: the pipeline conflates
 *      frames anyway, so a faster stream only costs USB bandwidth and conversion time;
 *    - otherwise MJPEG at its highest rate;
 *    - otherwise YUYV at its highest rate.
 *
 * Modes with no frame rate are ignored. Returns null when nothing is usable.
 */
internal class CaptureModeSelector(
    private val targetWidth: Int = DEFAULT_WIDTH,
    private val targetHeight: Int = DEFAULT_HEIGHT,
    private val minYuyvFps: Int = DEFAULT_MIN_YUYV_FPS,
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
        return yuyv(atSize, lowestReaching = minYuyvFps) ?: mjpeg(atSize) ?: yuyv(atSize, lowestReaching = 0)
    }

    private fun yuyv(
        modes: List<SupportedMode>,
        lowestReaching: Int,
    ): CaptureMode? {
        val offered = modes.filter { it.format == CaptureFormat.YUYV }
        val fps =
            if (lowestReaching > 0) {
                offered.flatMap { it.fps }.filter { it >= lowestReaching }.minOrNull()
            } else {
                offered.flatMap { it.fps }.maxOrNull()
            } ?: return null
        return CaptureMode(CaptureFormat.YUYV, offered.first().width, offered.first().height, fps)
    }

    private fun mjpeg(modes: List<SupportedMode>): CaptureMode? {
        val offered = modes.filter { it.format == CaptureFormat.MJPEG }
        val fps = offered.flatMap { it.fps }.maxOrNull() ?: return null
        return CaptureMode(CaptureFormat.MJPEG, offered.first().width, offered.first().height, fps)
    }

    private companion object {
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        const val DEFAULT_MIN_YUYV_FPS = 30
    }
}
