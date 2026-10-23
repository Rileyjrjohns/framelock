package dev.framelock.live.system

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import dev.framelock.kernel.port.Clock
import dev.framelock.live.domain.port.MemoryPressureMonitor
import dev.framelock.live.domain.system.MemoryPressure
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Memory pressure from two signals, the worse of which wins (ADR-017):
 *
 * - `onTrimMemory` and `onLowMemory`, through a `ComponentCallbacks2` registered on the application context. From
 *   API 34 the system sends neither the `RUNNING_*` levels nor `onLowMemory`, so there only the levels that [TrimLevels]
 *   ignores can arrive and this signal is silent; each signal is held for [TRIM_HOLD], then forgotten.
 * - `ActivityManager.getMemoryInfo`, polled every [POLL_INTERVAL] and mapped by [MemoryInfoPressure]. This is the
 *   signal that works on every API level.
 *
 * Callbacks and polling run only while the flow is collected. The Java heap is not observed (ADR-017).
 */
class AndroidMemoryPressureMonitor(
    context: Context,
    private val clock: Clock,
) : MemoryPressureMonitor {
    private val appContext = context.applicationContext

    override val pressure: Flow<MemoryPressure> =
        callbackFlow {
            val manager =
                checkNotNull(appContext.getSystemService(ActivityManager::class.java)) { "No ActivityManager" }
            val info = ActivityManager.MemoryInfo()
            val trims = TrimHold(TRIM_HOLD.inWholeNanoseconds)
            val wake = Channel<Unit>(Channel.CONFLATED)
            val callbacks =
                object : ComponentCallbacks2 {
                    override fun onTrimMemory(level: Int) = signal(TrimLevels.of(level))

                    // Deprecated, and never called since API 34; still the whole-device signal before that.
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onLowMemory() = signal(MemoryPressure.CRITICAL)

                    override fun onConfigurationChanged(newConfig: Configuration) = Unit

                    private fun signal(pressure: MemoryPressure?) {
                        if (pressure == null) return
                        trims.record(pressure, clock.nowNanos())
                        wake.trySend(Unit)
                    }
                }
            appContext.registerComponentCallbacks(callbacks)
            launch {
                var polled = MemoryPressure.NORMAL
                var nextPollNanos = 0L
                while (true) {
                    val now = clock.nowNanos()
                    if (now >= nextPollNanos) {
                        manager.getMemoryInfo(info)
                        polled = MemoryInfoPressure.of(info.availMem, info.threshold, info.lowMemory, polled)
                        nextPollNanos = now + POLL_INTERVAL.inWholeNanoseconds
                    }
                    send(worseOf(polled, trims.current(now)))
                    // Wakes at once on a signal, otherwise when the next poll is due or the held signal may lapse.
                    withTimeoutOrNull(POLL_INTERVAL) { wake.receive() }
                }
            }
            awaitClose { appContext.unregisterComponentCallbacks(callbacks) }
        }.distinctUntilChanged()

    companion object {
        val POLL_INTERVAL: Duration = 2.seconds
        val TRIM_HOLD: Duration = 30.seconds
    }
}
