package dev.framelock.testing

import dev.framelock.live.domain.port.MemoryPressureMonitor
import dev.framelock.live.domain.system.MemoryPressure
import kotlinx.coroutines.flow.Flow

/** A [MemoryPressureMonitor] whose pressure the test sets. Thread-safe. */
class FakeMemoryPressureMonitor(
    initial: MemoryPressure = MemoryPressure.NORMAL,
) : MemoryPressureMonitor {
    private val state = FailableState(initial)

    override val pressure: Flow<MemoryPressure> = state.flow

    fun set(pressure: MemoryPressure) {
        state.set(pressure)
    }

    /** Makes the flow throw [error], for the collector that is running and for new ones, until the next [set]. */
    fun fail(error: Throwable) {
        state.fail(error)
    }
}
