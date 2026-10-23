package dev.framelock.core.domain.policy

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * @property baseDelay delay before the first retry; positive and finite.
 * @property maxDelay cap on every delay, jitter included; finite and at least [baseDelay].
 * @property jitter fraction of the nominal delay by which a delay may vary up or down, from 0.0 (none) to 1.0.
 */
data class ReconnectConfig(
    val baseDelay: Duration,
    val maxDelay: Duration,
    val jitter: Double,
) {
    init {
        require(baseDelay.isFinite() && baseDelay > Duration.ZERO) { "baseDelay must be finite and > 0: $baseDelay" }
        require(maxDelay.isFinite() && maxDelay >= baseDelay) { "maxDelay must be finite and >= baseDelay: $maxDelay" }
        require(jitter in 0.0..1.0) { "jitter must be within 0.0..1.0, was $jitter" }
    }
}

/** Exponential backoff with a cap and jitter. [random] is injected so tests can seed it. Not thread-safe. */
class ReconnectPolicy(
    private val config: ReconnectConfig,
    private val random: Random,
) {
    /** Delay before retry number [attempt] (0 for the first retry): always within `(0, maxDelay]`. */
    fun delayFor(attempt: Int): Duration {
        require(attempt >= 0) { "attempt must be >= 0, was $attempt" }
        val draw = random.nextDouble(-1.0, 1.0)
        return (nominalDelay(attempt) * (1 + config.jitter * draw)).coerceIn(SMALLEST_DELAY, config.maxDelay)
    }

    /** Doubles the base delay per attempt, stopping at the cap so a huge attempt number cannot overflow. */
    private fun nominalDelay(attempt: Int): Duration {
        var delay = config.baseDelay
        var doublings = attempt
        while (doublings > 0 && delay < config.maxDelay) {
            delay *= 2
            doublings--
        }
        return minOf(delay, config.maxDelay)
    }

    private companion object {
        val SMALLEST_DELAY = 1.nanoseconds
    }
}
