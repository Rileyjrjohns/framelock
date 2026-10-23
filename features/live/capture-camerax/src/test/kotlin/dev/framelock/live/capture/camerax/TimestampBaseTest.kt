package dev.framelock.live.capture.camerax

import dev.framelock.live.domain.frame.TimestampSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimestampBaseTest {
    @Test
    fun `a sensor timestamp close to realtime is on the realtime base`() {
        assertEquals(TimestampSource.REALTIME, TimestampBase.of(sensorNanos = 5_000_000_000L, realtimeNanos = 5_030_000_000L))
    }

    @Test
    fun `a sensor timestamp far from realtime is on another base`() {
        assertEquals(TimestampSource.ARRIVAL, TimestampBase.of(sensorNanos = 1_000_000L, realtimeNanos = 90_000_000_000L))
    }
}
