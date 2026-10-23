package dev.framelock.live.application.metrics

import dev.framelock.kernel.port.Clock

/** A clock the test moves by hand. Readable from several threads. */
internal class ManualClock(
    startNanos: Long = 0L,
) : Clock {
    @Volatile
    var now: Long = startNanos

    override fun nowNanos(): Long = now
}
