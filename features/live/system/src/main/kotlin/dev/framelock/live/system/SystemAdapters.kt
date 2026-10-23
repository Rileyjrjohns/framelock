package dev.framelock.live.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.tracing.Trace
import dev.framelock.kernel.port.Clock
import dev.framelock.kernel.port.TraceSection
import dev.framelock.kernel.port.Tracer
import dev.framelock.live.domain.port.DeviceInfoProvider
import dev.framelock.live.domain.port.MemoryPressureMonitor
import dev.framelock.live.domain.port.ThermalMonitor
import dev.framelock.live.domain.system.DeviceSnapshot
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** ADR-011: the realtime base, the same as camera timestamps on most devices. */
class AndroidClock : Clock {
    override fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()
}

/** ADR-009: androidx.tracing sections, visible in Perfetto. Sections end on the thread that began them. */
class AndroidTracer : Tracer {
    override fun begin(section: TraceSection) = Trace.beginSection(section.name)

    override fun end() = Trace.endSection()
}

/** The thermal status listener (API 29+): the current status first, then every change. */
class PowerManagerThermalMonitor(
    private val context: Context,
) : ThermalMonitor {
    override val level: Flow<ThermalLevel> =
        callbackFlow {
            val power = checkNotNull(context.getSystemService(PowerManager::class.java)) { "No PowerManager" }
            val listener = PowerManager.OnThermalStatusChangedListener { trySend(ThermalLevels.of(it)) }
            trySend(ThermalLevels.of(power.currentThermalStatus))
            power.addThermalStatusListener(ContextCompat.getMainExecutor(context), listener)
            awaitClose { power.removeThermalStatusListener(listener) }
        }.distinctUntilChanged()
}

/**
 * Memory pressure is not observed yet: M4 adds `onTrimMemory` (checking which levels API 34+ still delivers) and
 * `ActivityManager.MemoryInfo` polling. Until then the pipeline sees NORMAL.
 */
class NotYetObservedMemoryPressure : MemoryPressureMonitor {
    override val pressure: Flow<MemoryPressure> = MutableStateFlow(MemoryPressure.NORMAL)
}

/** Device model, OS version and battery from the sticky battery intent (temperature in tenths of °C). */
class AndroidDeviceInfoProvider(
    private val context: Context,
) : DeviceInfoProvider {
    override fun snapshot(): DeviceSnapshot {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val tenthsOfDegree = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val power = context.getSystemService(PowerManager::class.java)
        return DeviceSnapshot(
            model = BoundedToken.of(Build.MODEL),
            osVersion = BoundedToken.of(Build.VERSION.RELEASE),
            batteryPct = Battery.percent(level, scale),
            batteryTempC = tenthsOfDegree / TENTHS,
            thermalLevel = ThermalLevels.of(power?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE),
        )
    }

    private companion object {
        const val TENTHS = 10f
    }
}

internal object ThermalLevels {
    /** Unknown statuses (a newer platform) are treated as the worst, so the pipeline errs on the safe side. */
    fun of(status: Int): ThermalLevel =
        when (status) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalLevel.EMERGENCY
            else -> ThermalLevel.SHUTDOWN
        }
}

/** ADR-004: device strings sent as telemetry are reduced to `[A-Za-z0-9._ -]{1,64}`. */
internal object BoundedToken {
    private const val MAX_LENGTH = 64
    private val OUTSIDE = Regex("[^A-Za-z0-9._ -]")

    fun of(raw: String?): String = raw.orEmpty().replace(OUTSIDE, "").trim().take(MAX_LENGTH).ifEmpty { "unknown" }
}

internal object Battery {
    private const val PERCENT = 100

    /** 0..100; an unknown level or scale gives 0. */
    fun percent(
        level: Int,
        scale: Int,
    ): Int = if (level < 0 || scale <= 0) 0 else (level * PERCENT / scale).coerceIn(0, PERCENT)
}
