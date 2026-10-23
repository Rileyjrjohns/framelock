package dev.framelock.core.application.metrics

import dev.framelock.core.domain.port.Clock

/** A clock the test moves by hand. Readable from several threads. */
internal class ManualClock(
    startNanos: Long = 0L,
) : Clock {
    @Volatile
    var now: Long = startNanos

    override fun nowNanos(): Long = now
}
