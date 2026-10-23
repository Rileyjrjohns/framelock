package dev.framelock.live.application.metrics

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class PercentilesTest {
    @Test
    fun `nearest-rank p50 and p95 of 1 to 100 are 50 and 95`() {
        val samples = LongArray(100) { it + 1L }
        val scratch = LongArray(100)

        assertEquals(50L, Percentiles.nearestRank(samples, 100, 50, scratch))
        assertEquals(95L, Percentiles.nearestRank(samples, 100, 95, scratch))
    }

    @Test
    fun `single sample gives that sample for every percentile`() {
        val samples = longArrayOf(42)
        val scratch = LongArray(1)

        listOf(0, 1, 50, 95, 100).forEach { percentile ->
            assertEquals(42L, Percentiles.nearestRank(samples, 1, percentile, scratch), "p$percentile")
        }
    }

    @Test
    fun `even count uses nearest-rank, not interpolation`() {
        val samples = longArrayOf(10, 20, 30, 40)
        val scratch = LongArray(4)

        // Interpolation would give 25 for the median; nearest-rank picks rank ceil(0.5 * 4) = 2.
        assertEquals(20L, Percentiles.nearestRank(samples, 4, 50, scratch))
        // Rank ceil(0.95 * 4) = 4.
        assertEquals(40L, Percentiles.nearestRank(samples, 4, 95, scratch))
    }

    @Test
    fun `result is independent of insertion order`() {
        val ascending = LongArray(20) { (it + 1) * 7L }
        val shuffled = ascending.toList().shuffled(Random(SEED)).toLongArray()
        val scratch = LongArray(20)

        listOf(50, 95).forEach { percentile ->
            assertEquals(
                Percentiles.nearestRank(ascending, 20, percentile, scratch),
                Percentiles.nearestRank(shuffled, 20, percentile, scratch),
                "p$percentile",
            )
        }
    }

    @Test
    fun `computation reuses the caller-provided buffer`() {
        val samples = longArrayOf(5, 3, 1, 4, 2)
        val scratch = LongArray(5)

        Percentiles.nearestRank(samples, 5, 50, scratch)

        assertArrayEquals(longArrayOf(1, 2, 3, 4, 5), scratch, "scratch holds the sorted copy")
        assertArrayEquals(longArrayOf(5, 3, 1, 4, 2), samples, "samples are untouched")
    }

    @Test
    fun `only the first count samples are considered and the rest of the buffers is left alone`() {
        val samples = longArrayOf(9, 1, 5, 1_000, 2_000)
        val scratch = longArrayOf(-1, -1, -1, -1, -1, -1)

        val p95 = Percentiles.nearestRank(samples, 3, 95, scratch)

        assertEquals(9L, p95)
        assertArrayEquals(longArrayOf(1, 5, 9, -1, -1, -1), scratch)
    }

    @Test
    fun `zero samples gives no percentile`() {
        assertNull(Percentiles.nearestRank(LongArray(4), 0, 50, LongArray(4)))
        assertNull(Percentiles.nearestRank(LongArray(0), 0, 95, LongArray(0)))
    }

    @Test
    fun `invalid arguments are rejected`() {
        val samples = LongArray(4)

        assertThrows<IllegalArgumentException> { Percentiles.nearestRank(samples, 4, -1, LongArray(4)) }
        assertThrows<IllegalArgumentException> { Percentiles.nearestRank(samples, 4, 101, LongArray(4)) }
        assertThrows<IllegalArgumentException> { Percentiles.nearestRank(samples, 5, 50, LongArray(5)) }
        assertThrows<IllegalArgumentException> { Percentiles.nearestRank(samples, 4, 50, LongArray(3)) }
        assertThrows<IllegalArgumentException> { Percentiles.nearestRank(samples, -1, 50, LongArray(4)) }
    }

    private companion object {
        const val SEED = 7
    }
}
