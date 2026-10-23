package dev.framelock.live.system

import dev.framelock.live.domain.system.ThermalLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

/**
 * Turns the forecast of `PowerManager.getThermalHeadroom` (API 30+) into a [ThermalLevel], so that the pipeline can
 * slow down before the platform throttles (ADR-017).
 *
 * The platform defines the scale: 1.0 is the `THERMAL_STATUS_SEVERE` throttling threshold, values in (0, 1] scale
 * linearly with temperature, and values above 1.0 have no implied mapping to a status. So the forecast can name the
 * levels below SEVERE with some meaning, and SEVERE at most; the listener keeps authority over anything worse.
 *
 * | Forecast headroom | Level |
 * |---|---|
 * | below 0.80 | NONE |
 * | 0.80 up to 0.90 | LIGHT (no rate cap, ADR-013) |
 * | 0.90 up to 1.00 | MODERATE (15 fps) |
 * | 1.00 and above | SEVERE (10 fps) |
 *
 * A level is left only once the forecast is [RELEASE_MARGIN] below the threshold that entered it, so a forecast
 * hovering on a threshold (the rate cap itself lowers the temperature) does not flip the rate every poll.
 */
internal object HeadroomLevels {
    const val LIGHT_AT = 0.80f
    const val MODERATE_AT = 0.90f
    const val SEVERE_AT = 1.0f
    const val RELEASE_MARGIN = 0.05f

    /** The level for [headroom] given the [previous] one, or null when the value says nothing (NaN, infinite). */
    fun of(
        headroom: Float,
        previous: ThermalLevel,
    ): ThermalLevel? {
        if (!headroom.isFinite()) return null
        val raw = rawLevel(headroom)
        if (raw >= previous) return raw
        return minOf(previous, rawLevel(headroom + RELEASE_MARGIN))
    }

    private fun rawLevel(headroom: Float): ThermalLevel =
        when {
            headroom >= SEVERE_AT -> ThermalLevel.SEVERE
            headroom >= MODERATE_AT -> ThermalLevel.MODERATE
            headroom >= LIGHT_AT -> ThermalLevel.LIGHT
            else -> ThermalLevel.NONE
        }
}

/** The worse of two levels: the pipeline degrades on whichever signal is more severe (ADR-017). */
internal fun worseOf(
    first: ThermalLevel,
    second: ThermalLevel,
): ThermalLevel = maxOf(first, second)

/**
 * Polls [read] (a forecast headroom) every [interval] and emits the level it implies, starting with NONE.
 * A NaN (unsupported, or called too soon) keeps the last level; [read] failing counts as NaN. Runs only while
 * collected.
 */
internal fun headroomLevels(
    interval: Duration,
    read: () -> Float,
): Flow<ThermalLevel> =
    flow {
        var level = ThermalLevel.NONE
        emit(level)
        while (true) {
            delay(interval)
            val next = HeadroomLevels.of(readOrNaN(read), level)
            if (next != null && next != level) {
                level = next
                emit(level)
            }
        }
    }

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun readOrNaN(read: () -> Float): Float =
    try {
        read()
    } catch (e: RuntimeException) {
        // The thermal service can fail (it rethrows a dead system server); the listener still guards the device.
        Float.NaN
    }
