package dev.framelock.core.testing

import dev.framelock.core.domain.port.ThermalMonitor
import dev.framelock.core.domain.system.ThermalLevel
import kotlinx.coroutines.flow.Flow

/** A [ThermalMonitor] whose level the test sets. Thread-safe. */
class FakeThermalMonitor(
    initial: ThermalLevel = ThermalLevel.NONE,
) : ThermalMonitor {
    private val state = FailableState(initial)

    override val level: Flow<ThermalLevel> = state.flow

    fun set(level: ThermalLevel) {
        state.set(level)
    }

    /** Makes the flow throw [error], for the collector that is running and for new ones, until the next [set]. */
    fun fail(error: Throwable) {
        state.fail(error)
    }
}
