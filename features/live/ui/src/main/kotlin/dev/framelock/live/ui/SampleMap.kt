package dev.framelock.live.ui

/**
 * For each target index along one axis, the source index it samples (nearest neighbour, taken at the centre of the
 * target cell). The table is rebuilt only when the (source, target) pair changes, so steady state does no work and no
 * allocation here, and the per-pixel loops need no division. Target equal to source gives the identity.
 */
internal class SampleMap {
    private var source = 0
    private var target = 0
    private var table = IntArray(0)

    fun indices(
        source: Int,
        target: Int,
    ): IntArray {
        if (source == this.source && target == this.target) return table
        if (table.size < target) table = IntArray(target)
        for (i in 0 until target) {
            table[i] = ((2L * i + 1) * source / (2L * target)).toInt()
        }
        this.source = source
        this.target = target
        return table
    }
}
