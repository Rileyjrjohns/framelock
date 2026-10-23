package dev.framelock.live.domain.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class ReconnectPolicyTest {
    @Test
    fun `first delay equals base within jitter bounds`() {
        val base = 1.seconds
        val jitter = 0.2

        // Without jitter the first delay is exactly the base.
        assertEquals(base, policy(base = base, jitter = 0.0, seed = 0).delayFor(0))

        // With jitter it stays within plus or minus 20 % of the base.
        (0..99).forEach { seed ->
            val delay = policy(base = base, jitter = jitter, seed = seed).delayFor(0)
            assertTrue(delay in base * (1 - jitter)..base * (1 + jitter), "seed $seed gave $delay")
        }
    }

    @Test
    fun `delay doubles per attempt when jitter is zero`() {
        val policy = policy(base = 100.milliseconds, max = 1.days, jitter = 0.0, seed = 0)

        (0..10).forEach { attempt ->
            assertEquals(100.milliseconds * (1 shl attempt), policy.delayFor(attempt), "attempt $attempt")
        }
    }

    @Test
    fun `delay never exceeds the cap, jitter included`() {
        val max = 30.seconds
        var capped = 0

        (0..99).forEach { seed ->
            val policy = policy(base = 1.seconds, max = max, jitter = 0.5, seed = seed)
            (0..100).forEach { attempt ->
                val delay = policy.delayFor(attempt)
                assertTrue(delay <= max, "seed $seed, attempt $attempt gave $delay")
                if (delay == max) capped++
            }
        }

        // Past the cap, upward jitter is clamped to it, so the cap is reached.
        assertTrue(capped > 0, "the cap was never reached")
    }

    @Test
    fun `delay is always positive`() {
        (0..99).forEach { seed ->
            val policy = policy(base = 1.nanoseconds, max = 1.seconds, jitter = 1.0, seed = seed)
            (0..100).forEach { attempt ->
                val delay = policy.delayFor(attempt)
                assertTrue(delay > Duration.ZERO, "seed $seed, attempt $attempt gave $delay")
            }
        }

        // The lowest draw (-1) with the widest jitter (1.0) would give a factor of 0, hence a delay of 0.
        val lowestDraw = policy(base = 1.seconds, jitter = 1.0, random = LowestDraw)
        assertTrue(lowestDraw.delayFor(0) > Duration.ZERO)
    }

    @Test
    fun `jittered delay stays within plus or minus factor of nominal`() {
        val jitter = 0.25
        val nominal = 8.seconds // base 1 s, attempt 3
        val policy = ReconnectPolicy(ReconnectConfig(1.seconds, 1.days, jitter), Random(42))

        val delays = List(10_000) { policy.delayFor(attempt = 3) }

        assertTrue(delays.all { it in nominal * (1 - jitter)..nominal * (1 + jitter) }, "a draw left the bounds")
        // The jitter is really applied, on both sides of the nominal delay.
        assertTrue(delays.any { it < nominal }, "no draw below nominal")
        assertTrue(delays.any { it > nominal }, "no draw above nominal")
    }

    @Test
    fun `same seed gives the same sequence`() {
        val first = policy(seed = 7)
        val second = policy(seed = 7)

        assertEquals((0..49).map { first.delayFor(it) }, (0..49).map { second.delayFor(it) })
    }

    @Test
    fun `fixed seeds 1 and 2 give different sequences`() {
        val first = policy(seed = 1)
        val second = policy(seed = 2)

        assertNotEquals((0..49).map { first.delayFor(it) }, (0..49).map { second.delayFor(it) })
    }

    @Test
    fun `huge attempt numbers do not overflow`() {
        val max = 30.seconds

        // Without jitter the cap is the exact answer.
        val exact = policy(base = 1.seconds, max = max, jitter = 0.0, seed = 0)
        listOf(62, 63, 64, 1_000, Int.MAX_VALUE).forEach { attempt ->
            assertEquals(max, exact.delayFor(attempt), "attempt $attempt")
        }

        // With jitter the delay stays in (0, max].
        val jittered = policy(base = 1.seconds, max = max, jitter = 0.5, seed = 3)
        val delay = jittered.delayFor(Int.MAX_VALUE)
        assertTrue(delay > Duration.ZERO && delay <= max, "gave $delay")

        // Delays near the limit of the nanosecond range saturate instead of wrapping around.
        val huge = policy(base = 100_000.days, max = 1_000_000.days, jitter = 0.0, seed = 0)
        assertEquals(1_000_000.days, huge.delayFor(5))
    }

    @Test
    fun `invalid config is rejected`() {
        val invalid =
            listOf<() -> ReconnectConfig>(
                { ReconnectConfig(Duration.ZERO, 1.seconds, 0.1) },
                { ReconnectConfig((-1).seconds, 1.seconds, 0.1) },
                { ReconnectConfig(2.seconds, 1.seconds, 0.1) },
                { ReconnectConfig(1.seconds, 1.seconds, -0.1) },
                { ReconnectConfig(1.seconds, 1.seconds, 1.1) },
                { ReconnectConfig(1.seconds, 1.seconds, Double.NaN) },
                { ReconnectConfig(Duration.INFINITE, Duration.INFINITE, 0.1) },
                { ReconnectConfig(1.seconds, Duration.INFINITE, 0.1) },
            )

        invalid.forEachIndexed { index, build ->
            assertThrows<IllegalArgumentException>("invalid config #$index") { build() }
        }
        // The limits themselves are valid.
        ReconnectConfig(1.seconds, 1.seconds, 0.0)
        ReconnectConfig(1.nanoseconds, 1.days, 1.0)
    }

    @Test
    fun `negative attempt is rejected`() {
        assertThrows<IllegalArgumentException> { policy(seed = 0).delayFor(-1) }
    }

    private fun policy(
        base: Duration = 1.seconds,
        max: Duration = 1.days,
        jitter: Double = 0.2,
        seed: Int,
    ) = policy(base, max, jitter, Random(seed))

    private fun policy(
        base: Duration = 1.seconds,
        max: Duration = 1.days,
        jitter: Double = 0.2,
        random: Random,
    ) = ReconnectPolicy(ReconnectConfig(base, max, jitter), random)

    /** Every draw is the lowest one: `nextDouble(-1.0, 1.0)` returns exactly -1.0. */
    private object LowestDraw : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }
}
