package dev.framelock.core.application.metrics

/** Percentile computation that allocates nothing: the caller provides the working buffer (ADR-012). */
object Percentiles {
    private const val MAX_PERCENTILE = 100

    /**
     * Nearest-rank percentile of `samples[0 until count]`: the value at rank `ceil(percentile / 100 * count)`, with
     * a minimum rank of 1. No interpolation, so the result is always one of the samples.
     *
     * Sorts a copy of the samples into [scratch], which the caller reuses from call to call; [samples] is not
     * modified, and entries of [scratch] from [count] on are not touched. Returns null when [count] is 0.
     */
    fun nearestRank(
        samples: LongArray,
        count: Int,
        percentile: Int,
        scratch: LongArray,
    ): Long? {
        require(percentile in 0..MAX_PERCENTILE) { "percentile must be within 0..100, was $percentile" }
        require(count in 0..samples.size) { "count must be within 0..${samples.size}, was $count" }
        require(scratch.size >= count) { "scratch holds ${scratch.size} entries, need at least $count" }
        if (count == 0) return null

        samples.copyInto(scratch, destinationOffset = 0, startIndex = 0, endIndex = count)
        scratch.sort(fromIndex = 0, toIndex = count)

        val rank = ((percentile.toLong() * count + MAX_PERCENTILE - 1) / MAX_PERCENTILE).toInt().coerceAtLeast(1)
        return scratch[rank - 1]
    }
}
