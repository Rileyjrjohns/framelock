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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** ADR-011: the realtime base, the same as camera timestamps on most devices. */
class AndroidClock : Clock {
    override fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()
}

/** ADR-009: androidx.tracing sections, visible in Perfetto. Sections end on the thread that began them. */
class AndroidTracer : Tracer {
    override fun begin(section: TraceSection) = Trace.beginSection(section.name)

    override fun end() = Trace.endSection()
}

/**
 * The thermal level, the worse of two signals (ADR-017):
 *
 * - the thermal status listener (API 29+): the current status first, then every change;
 * - from API 30, the forecast headroom (`getThermalHeadroom`, [FORECAST]) polled every [HEADROOM_INTERVAL] and mapped
 *   by [HeadroomLevels], so the pipeline slows down before the platform throttles.
 *
 * Both run only while the flow is collected. On API 29 only the listener is used.
 */
class PowerManagerThermalMonitor(
    private val context: Context,
) : ThermalMonitor {
    override val level: Flow<ThermalLevel> =
        flow {
            val power = checkNotNull(context.getSystemService(PowerManager::class.java)) { "No PowerManager" }
            val statuses = statusLevels(power)
            val headroom =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    headroomLevels(HEADROOM_INTERVAL) { power.getThermalHeadroom(FORECAST_SECONDS) }
                } else {
                    flowOf(ThermalLevel.NONE)
                }
            emitAll(combine(statuses, headroom, ::worseOf))
        }.distinctUntilChanged()

    private fun statusLevels(power: PowerManager): Flow<ThermalLevel> =
        callbackFlow {
            val listener = PowerManager.OnThermalStatusChangedListener { trySend(ThermalLevels.of(it)) }
            trySend(ThermalLevels.of(power.currentThermalStatus))
            power.addThermalStatusListener(ContextCompat.getMainExecutor(context), listener)
            awaitClose { power.removeThermalStatusListener(listener) }
        }

    companion object {
        /** The platform advises against calling more than about once per second. */
        val HEADROOM_INTERVAL: Duration = 2.seconds

        /** Far enough ahead to act on, near enough to stay accurate; the allowed range is 0..60 s. */
        const val FORECAST_SECONDS = 10
    }
}

/** One subscription to [upstream] for every collector (the pipeline and the debug HUD), kept while any is collecting. */
class SharedThermalMonitor(
    upstream: ThermalMonitor,
    scope: CoroutineScope,
) : ThermalMonitor {
    val state: StateFlow<ThermalLevel> =
        upstream.level.stateIn(scope, SharingStarted.WhileSubscribed(KEEP_ALIVE_MS), ThermalLevel.NONE)
    override val level: Flow<ThermalLevel> = state
}

/** One subscription to [upstream] for every collector (the pipeline and the debug HUD), kept while any is collecting. */
class SharedMemoryPressureMonitor(
    upstream: MemoryPressureMonitor,
    scope: CoroutineScope,
) : MemoryPressureMonitor {
    val state: StateFlow<MemoryPressure> =
        upstream.pressure.stateIn(scope, SharingStarted.WhileSubscribed(KEEP_ALIVE_MS), MemoryPressure.NORMAL)
    override val pressure: Flow<MemoryPressure> = state
}

private const val KEEP_ALIVE_MS = 5_000L

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

    fun of(raw: String?): String =
        raw
            .orEmpty()
            .replace(OUTSIDE, "")
            .trim()
            .take(MAX_LENGTH)
            .ifEmpty { "unknown" }
}

internal object Battery {
    private const val PERCENT = 100

    /** 0..100; an unknown level or scale gives 0. */
    fun percent(
        level: Int,
        scale: Int,
    ): Int = if (level < 0 || scale <= 0) 0 else (level * PERCENT / scale).coerceIn(0, PERCENT)
}
