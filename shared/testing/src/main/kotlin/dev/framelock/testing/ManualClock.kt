package dev.framelock.testing

import dev.framelock.kernel.port.Clock
import java.util.concurrent.atomic.AtomicLong

/** A [Clock] that only moves when the test says so. Thread-safe; time never goes backwards (ADR-011). */
class ManualClock(
    startNanos: Long = 0,
) : Clock {
    init {
        require(startNanos >= 0) { "startNanos must be >= 0, was $startNanos" }
    }

    private val now = AtomicLong(startNanos)

    override fun nowNanos(): Long = now.get()

    fun advanceBy(nanos: Long) {
        require(nanos >= 0) { "A clock cannot go backwards: advanceBy($nanos)" }
        now.addAndGet(nanos)
    }

    fun set(nanos: Long) {
        now.updateAndGet { current ->
            require(nanos >= current) { "A clock cannot go backwards: set($nanos) while at $current" }
            nanos
        }
    }
}
