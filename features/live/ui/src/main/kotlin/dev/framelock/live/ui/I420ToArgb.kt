package dev.framelock.live.ui

import dev.framelock.live.domain.frame.Plane

/**
 * YUV 4:2:0 to opaque ARGB pixels into a caller-owned array, for the bitmap the screen draws (ADR-010: the renderer
 * copies the analysed frame). BT.601 full range, fixed point, no allocation in steady state (ADR-012).
 *
 * The output may be smaller than the frame: each output pixel samples one source pixel (nearest neighbour, see
 * [SampleMap]), so the cost is proportional to the output pixels, not the frame's. A 1920x1080 frame shown in 1080x608
 * converts 0.66 M pixels instead of 2.07 M. Source rows are read in bulk into reused arrays, column offsets come from
 * cached tables, the chroma products come from 256-entry tables and the clamp is a table lookup (no branch per pixel).
 * An instance holds its scratch buffers and caches: one per renderer, main thread only.
 */
internal class I420ToArgb {
    private val rowMap = SampleMap()
    private val columnMap = SampleMap()
    private val yReader = RowReader()
    private val uReader = RowReader()
    private val vReader = RowReader()
    private var yRow = ByteArray(0)
    private var uRow = ByteArray(0)
    private var vRow = ByteArray(0)
    private var yOffsets = IntArray(0)
    private var uOffsets = IntArray(0)
    private var vOffsets = IntArray(0)
    private var offsetsKey = 0L
    private var offsetsWidth = 0
    private var offsetsSource = 0

    /** Converts the whole frame at its own size: [out] holds `width * height` pixels. */
    fun convert(
        planes: List<Plane>,
        width: Int,
        height: Int,
        out: IntArray,
    ) = convert(planes, width, height, out, width, height)

    /** Converts a [width] x [height] frame into [outWidth] x [outHeight] pixels (each no larger than the frame's). */
    fun convert(
        planes: List<Plane>,
        width: Int,
        height: Int,
        out: IntArray,
        outWidth: Int,
        outHeight: Int,
    ) {
        val (y, u, v) = planes
        val rows = rowMap.indices(height, outHeight)
        val columns = columnMap.indices(width, outWidth)
        prepareOffsets(columns, width, outWidth, y.pixelStride, u.pixelStride, v.pixelStride)
        val chromaColumns = (width - 1) / 2
        yRow = grown(yRow, (width - 1) * y.pixelStride + 1)
        uRow = grown(uRow, chromaColumns * u.pixelStride + 1)
        vRow = grown(vRow, chromaColumns * v.pixelStride + 1)
        var lastLumaRow = -1
        var lastChromaRow = -1
        for (outRow in 0 until outHeight) {
            val sourceRow = rows[outRow]
            if (sourceRow != lastLumaRow) {
                yReader.read(y.buffer, sourceRow * y.rowStride, (width - 1) * y.pixelStride + 1, yRow)
                lastLumaRow = sourceRow
            }
            val chromaRow = sourceRow shr 1
            if (chromaRow != lastChromaRow) {
                uReader.read(u.buffer, chromaRow * u.rowStride, chromaColumns * u.pixelStride + 1, uRow)
                vReader.read(v.buffer, chromaRow * v.rowStride, chromaColumns * v.pixelStride + 1, vRow)
                lastChromaRow = chromaRow
            }
            val base = outRow * outWidth
            for (column in 0 until outWidth) {
                val luma = yRow[yOffsets[column]].toInt() and CHANNEL_MAX
                val cb = uRow[uOffsets[column]].toInt() and CHANNEL_MAX
                val cr = vRow[vOffsets[column]].toInt() and CHANNEL_MAX
                val r = CLAMP[luma + RED_FROM_CR[cr] + CLAMP_PAD]
                val g = CLAMP[luma - ((GREEN_FROM_CB[cb] + GREEN_FROM_CR[cr]) shr SHIFT) + CLAMP_PAD]
                val b = CLAMP[luma + BLUE_FROM_CB[cb] + CLAMP_PAD]
                out[base + column] = OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
            }
        }
    }

    /** Byte offsets of each output column inside its row, per plane; rebuilt only when sizes or strides change. */
    private fun prepareOffsets(
        columns: IntArray,
        width: Int,
        outWidth: Int,
        yStride: Int,
        uStride: Int,
        vStride: Int,
    ) {
        val key = (yStride.toLong() shl STRIDE_BITS * 2) or (uStride.toLong() shl STRIDE_BITS) or vStride.toLong()
        if (key == offsetsKey && width == offsetsSource && outWidth == offsetsWidth && yOffsets.size >= outWidth) return
        if (yOffsets.size < outWidth) {
            yOffsets = IntArray(outWidth)
            uOffsets = IntArray(outWidth)
            vOffsets = IntArray(outWidth)
        }
        for (i in 0 until outWidth) {
            yOffsets[i] = columns[i] * yStride
            uOffsets[i] = (columns[i] shr 1) * uStride
            vOffsets[i] = (columns[i] shr 1) * vStride
        }
        offsetsKey = key
        offsetsSource = width
        offsetsWidth = outWidth
    }

    private fun grown(
        array: ByteArray,
        size: Int,
    ): ByteArray = if (array.size >= size) array else ByteArray(size)

    private companion object {
        const val CHANNEL_MAX = 255
        const val CHROMA_OFFSET = 128
        const val OPAQUE = 0xFF shl 24
        const val SHIFT = 10
        const val R_V = 1436
        const val G_U = 352
        const val G_V = 731
        const val B_U = 1815
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val STRIDE_BITS = 16

        /** Largest offset a chroma term adds to luma is about 227: this pad keeps every index non-negative. */
        const val CLAMP_PAD = 256

        val CLAMP = IntArray(CHANNEL_MAX + 1 + 2 * CLAMP_PAD) { (it - CLAMP_PAD).coerceIn(0, CHANNEL_MAX) }
        val RED_FROM_CR = IntArray(CHANNEL_MAX + 1) { R_V * (it - CHROMA_OFFSET) shr SHIFT }
        val GREEN_FROM_CB = IntArray(CHANNEL_MAX + 1) { G_U * (it - CHROMA_OFFSET) }
        val GREEN_FROM_CR = IntArray(CHANNEL_MAX + 1) { G_V * (it - CHROMA_OFFSET) }
        val BLUE_FROM_CB = IntArray(CHANNEL_MAX + 1) { B_U * (it - CHROMA_OFFSET) shr SHIFT }
    }
}
