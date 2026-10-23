package dev.framelock.core.testing

import dev.framelock.core.domain.port.Clock

/**
 * A [Clock] that reads [timeSource] on every call. In a `runTest` block, pass the virtual time so that timestamps and
 * `delay` share one timeline (ADR-011), e.g. `FakeClock { testScheduler.currentTime * 1_000_000 }`.
 */
class FakeClock(
    private val timeSource: () -> Long,
) : Clock {
    override fun nowNanos(): Long = timeSource()
}
