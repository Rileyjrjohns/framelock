package dev.framelock.live.system

import dev.framelock.live.domain.system.MemoryPressure

/**
 * `onTrimMemory` levels as [MemoryPressure] (ADR-017). The values are those of `ComponentCallbacks2`, whose
 * `TRIM_MEMORY_RUNNING_*` levels are the only ones that describe the device running low while the app is in the
 * foreground. They are no longer delivered from API 34, so on those devices this mapping stays idle and polling
 * carries the load.
 */
internal object TrimLevels {
    private const val RUNNING_MODERATE = 5
    private const val RUNNING_CRITICAL = 15
    private const val UI_HIDDEN = 20

    /**
     * RUNNING_MODERATE and RUNNING_LOW give MODERATE, RUNNING_CRITICAL gives CRITICAL. UI_HIDDEN and the background
     * levels (BACKGROUND, MODERATE, COMPLETE) say where the process stands in the LRU list, not that the device is
     * short of memory for a running pipeline, so they give null (no signal).
     */
    fun of(level: Int): MemoryPressure? =
        when {
            level >= UI_HIDDEN -> null
            level >= RUNNING_CRITICAL -> MemoryPressure.CRITICAL
            level >= RUNNING_MODERATE -> MemoryPressure.MODERATE
            else -> null
        }
}

/**
 * `ActivityManager.MemoryInfo` as [MemoryPressure]. `threshold` is the available memory below which the system
 * starts killing background processes and `lowMemory` is true at or below it.
 *
 * | Available memory | Pressure |
 * |---|---|
 * | at or below the threshold, or `lowMemory` | CRITICAL (pause, ADR-013) |
 * | up to twice the threshold | MODERATE (15 fps) |
 * | above | NORMAL |
 *
 * A level is left only once the available memory is [RELEASE_FACTOR] above its bound, against flapping.
 */
internal object MemoryInfoPressure {
    const val MODERATE_FACTOR = 2.0
    const val RELEASE_FACTOR = 1.1

    fun of(
        availBytes: Long,
        thresholdBytes: Long,
        lowMemory: Boolean,
        previous: MemoryPressure,
    ): MemoryPressure {
        val criticalBound = thresholdBytes * boundFactor(previous >= MemoryPressure.CRITICAL)
        val moderateBound = thresholdBytes * MODERATE_FACTOR * boundFactor(previous >= MemoryPressure.MODERATE)
        return when {
            lowMemory || availBytes <= criticalBound -> MemoryPressure.CRITICAL
            availBytes <= moderateBound -> MemoryPressure.MODERATE
            else -> MemoryPressure.NORMAL
        }
    }

    private fun boundFactor(held: Boolean) = if (held) RELEASE_FACTOR else 1.0
}

/**
 * Holds the last `onTrimMemory` signal for [holdNanos], then lets it go. A trim callback is an event, with no
 * "all clear" counterpart, so without a decay one signal would hold the pipeline back for good. A lower signal
 * never cuts a higher one short. Thread-safe: callbacks arrive on the main thread, evaluation on the collector's.
 */
internal class TrimHold(
    private val holdNanos: Long,
) {
    private var held = MemoryPressure.NORMAL
    private var sinceNanos = 0L

    @Synchronized
    fun record(
        pressure: MemoryPressure,
        nowNanos: Long,
    ) {
        if (pressure >= current(nowNanos)) {
            held = pressure
            sinceNanos = nowNanos
        }
    }

    @Synchronized
    fun current(nowNanos: Long): MemoryPressure =
        if (held != MemoryPressure.NORMAL && nowNanos - sinceNanos < holdNanos) held else MemoryPressure.NORMAL
}

/** The worse of two pressures. */
internal fun worseOf(
    first: MemoryPressure,
    second: MemoryPressure,
): MemoryPressure = maxOf(first, second)
