package dev.framelock.live.ui

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.framelock.live.domain.inference.LabelId

/** Measured label text, keyed by (class, percent) so no string is built for a label already seen. */
internal class LabelLayouts(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val labelOf: (LabelId) -> String,
) {
    private val cache = HashMap<Int, TextLayoutResult>()

    fun layoutOf(
        label: LabelId,
        percent: Int,
    ): TextLayoutResult {
        val key = label.value * (PERCENT + 1) + percent
        cache[key]?.let { return it }
        if (cache.size >= MAX_CACHED_LABELS) cache.clear()
        return measurer
            .measure("${labelOf(label)} $percent%", style, overflow = TextOverflow.Ellipsis, maxLines = 1)
            .also { cache[key] = it }
    }
}
