package dev.framelock.live.inference.litert

import dev.framelock.live.domain.frame.Plane
import java.nio.ByteBuffer

/**
 * YUV 4:2:0 to packed RGB (one byte per channel), scaled with nearest-neighbour sampling into a caller-owned buffer:
 * the preprocessing step of a uint8 detection model. BT.601 full-range coefficients, integer arithmetic, no
 * allocation in steady state (ADR-012). Handles any row and pixel strides.
 *
 * The state (sampling tables, row and output scratch) lives in one [YuvConverter] per calling thread, so the engine's
 * inference thread owns its own and nothing is shared.
 */
internal object YuvToRgb {
    private val converters = ThreadLocal.withInitial { YuvConverter() }

    fun scaleInto(
        planes: List<Plane>,
        width: Int,
        height: Int,
        out: ByteBuffer,
        outWidth: Int,
        outHeight: Int,
    ) = converters.get().scaleInto(planes, width, height, out, outWidth, outHeight)
}

/**
 * The converter behind [YuvToRgb]. Output pixel (row, column) samples source pixel
 * (`row * height / outHeight`, `column * width / outWidth`) and the chroma at half those coordinates. The divisions
 * are done once into tables (source row indices, byte offsets of each sampled column per plane), rebuilt only when the
 * sizes or strides change. Each needed source row is read in bulk into a reused array, the chroma products come from
 * 256-entry tables and the clamp is a table lookup. The RGB bytes are assembled in a reused array and copied into the
 * destination with one bulk put. One instance per thread.
 */
internal class YuvConverter {
    private val yReader = RowReader()
    private val uReader = RowReader()
    private val vReader = RowReader()
    private var yRow = ByteArray(0)
    private var uRow = ByteArray(0)
    private var vRow = ByteArray(0)
    private var rows = IntArray(0)
    private var yOffsets = IntArray(0)
    private var uOffsets = IntArray(0)
    private var vOffsets = IntArray(0)
    private var rgb = ByteArray(0)
    private val tableKey = LongArray(KEY_SIZE)
    private var yLength = 0
    private var uLength = 0
    private var vLength = 0

    fun scaleInto(
        planes: List<Plane>,
        width: Int,
        height: Int,
        out: ByteBuffer,
        outWidth: Int,
        outHeight: Int,
    ) {
        val (y, u, v) = planes
        prepare(y, u, v, width, height, outWidth, outHeight)
        val size = outWidth * outHeight * RGB
        if (rgb.size < size) rgb = ByteArray(size)
        var lastRow = -1
        var target = 0
        for (outRow in 0 until outHeight) {
            val sourceRow = rows[outRow]
            if (sourceRow != lastRow) {
                val chromaRow = sourceRow shr 1
                yReader.read(y.buffer, sourceRow * y.rowStride, yLength, yRow)
                uReader.read(u.buffer, chromaRow * u.rowStride, uLength, uRow)
                vReader.read(v.buffer, chromaRow * v.rowStride, vLength, vRow)
                lastRow = sourceRow
            }
            for (column in 0 until outWidth) {
                val luma = yRow[yOffsets[column]].toInt() and CHANNEL_MAX
                val cb = uRow[uOffsets[column]].toInt() and CHANNEL_MAX
                val cr = vRow[vOffsets[column]].toInt() and CHANNEL_MAX
                rgb[target] = CLAMP[luma + RED_FROM_CR[cr] + CLAMP_PAD]
                rgb[target + 1] = CLAMP[luma - ((GREEN_FROM_CB[cb] + GREEN_FROM_CR[cr]) shr SHIFT) + CLAMP_PAD]
                rgb[target + 2] = CLAMP[luma + BLUE_FROM_CB[cb] + CLAMP_PAD]
                target += RGB
            }
        }
        out.put(rgb, 0, size)
    }

    /** Rebuilds the sampling tables and grows the row arrays when the sizes or strides differ from the last call. */
    private fun prepare(
        y: Plane,
        u: Plane,
        v: Plane,
        width: Int,
        height: Int,
        outWidth: Int,
        outHeight: Int,
    ) {
        val key = tableKey
        if (key[0] == width.toLong() && key[1] == height.toLong() && key[2] == outWidth.toLong() &&
            key[3] == outHeight.toLong() && key[4] == y.pixelStride.toLong() &&
            key[5] == u.pixelStride.toLong() && key[6] == v.pixelStride.toLong()
        ) {
            return
        }
        if (rows.size < outHeight) rows = IntArray(outHeight)
        if (yOffsets.size < outWidth) {
            yOffsets = IntArray(outWidth)
            uOffsets = IntArray(outWidth)
            vOffsets = IntArray(outWidth)
        }
        for (row in 0 until outHeight) rows[row] = row * height / outHeight
        for (column in 0 until outWidth) {
            val sourceColumn = column * width / outWidth
            yOffsets[column] = sourceColumn * y.pixelStride
            uOffsets[column] = (sourceColumn / 2) * u.pixelStride
            vOffsets[column] = (sourceColumn / 2) * v.pixelStride
        }
        // Offsets grow with the column: the last one bounds the bytes a row read has to cover.
        yLength = yOffsets[outWidth - 1] + 1
        uLength = uOffsets[outWidth - 1] + 1
        vLength = vOffsets[outWidth - 1] + 1
        if (yRow.size < yLength) yRow = ByteArray(yLength)
        if (uRow.size < uLength) uRow = ByteArray(uLength)
        if (vRow.size < vLength) vRow = ByteArray(vLength)
        key[0] = width.toLong()
        key[1] = height.toLong()
        key[2] = outWidth.toLong()
        key[3] = outHeight.toLong()
        key[4] = y.pixelStride.toLong()
        key[5] = u.pixelStride.toLong()
        key[6] = v.pixelStride.toLong()
    }

    private companion object {
        const val CHANNEL_MAX = 255
        const val CHROMA_OFFSET = 128
        const val RGB = 3
        const val KEY_SIZE = 7

        // BT.601 coefficients scaled by 2^10.
        const val SHIFT = 10
        const val R_V = 1436
        const val G_U = 352
        const val G_V = 731
        const val B_U = 1815

        /** The largest offset a chroma term adds to luma is about 227: this pad keeps every index non-negative. */
        const val CLAMP_PAD = 256

        val CLAMP =
            ByteArray(CHANNEL_MAX + 1 + 2 * CLAMP_PAD) { (it - CLAMP_PAD).coerceIn(0, CHANNEL_MAX).toByte() }
        val RED_FROM_CR = IntArray(CHANNEL_MAX + 1) { R_V * (it - CHROMA_OFFSET) shr SHIFT }
        val GREEN_FROM_CB = IntArray(CHANNEL_MAX + 1) { G_U * (it - CHROMA_OFFSET) }
        val GREEN_FROM_CR = IntArray(CHANNEL_MAX + 1) { G_V * (it - CHROMA_OFFSET) }
        val BLUE_FROM_CB = IntArray(CHANNEL_MAX + 1) { B_U * (it - CHROMA_OFFSET) shr SHIFT }
    }
}
