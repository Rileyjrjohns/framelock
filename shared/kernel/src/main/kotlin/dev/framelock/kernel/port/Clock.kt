package dev.framelock.kernel.port

/** Monotonic time on the realtime base (`elapsedRealtimeNanos` on Android, ADR-011). */
interface Clock {
    fun nowNanos(): Long
}
