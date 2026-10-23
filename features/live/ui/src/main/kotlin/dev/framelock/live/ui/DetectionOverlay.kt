package dev.framelock.live.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.framelock.live.application.pipeline.AnnotatedFrame
import dev.framelock.live.domain.inference.LabelId

private const val MAX_LABELS = 5
private const val PERCENT = 100

/** Past this many distinct (class, percent) labels the text cache starts over: it only guards against growth. */
private const val MAX_CACHED_LABELS = 512

private val BoxStroke = 2.5.dp
private val GlowStroke = 6.dp
private val BoxRadius = 14.dp
private val ChipPaddingX = 10.dp
private val ChipPaddingY = 5.dp
private val DotSize = 8.dp
private val DotGap = 6.dp
private const val GLOW_ALPHA = 0.35f
private val ChipFill = Color(GlassPalette.CHIP_FILL)

/**
 * Draws the analysed frame and, on the same pixels, its detections; then reports the render (ADR-011). The frame and
 * the boxes go through one [fitInto], so the overlay cannot drift from the picture (ADR-001).
 *
 * Boxes are drawn for every detection (rounded, in the class colour, with a soft glow of the same colour), labels for
 * the first [MAX_LABELS]: a dark pill with a dot in the class colour and white text, readable on any video. Label text
 * is measured once per (class, percent) and reused, so steady state allocates no text layouts.
 */
@Composable
internal fun AnalysedFrame(
    annotated: AnnotatedFrame,
    bitmapFor: (AnnotatedFrame) -> Bitmap?,
    labelOf: (LabelId) -> String,
    onDraw: (AnnotatedFrame) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style =
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR_NUMBERS)
    val labels = remember(labelOf, style, density) { LabelLayouts(measurer, style, labelOf) }
    val (boxStroke, glowStroke) =
        remember(density) { with(density) { Stroke(BoxStroke.toPx()) to Stroke(GlowStroke.toPx()) } }
    Canvas(modifier.fillMaxSize()) {
        val bitmap = bitmapFor(annotated) ?: return@Canvas
        val fit = fitInto(size.width, size.height, annotated.width.toFloat(), annotated.height.toFloat())
        drawImage(
            bitmap.asImageBitmap(),
            dstOffset = IntOffset(fit.left.toInt(), fit.top.toInt()),
            dstSize = IntSize(fit.width.toInt(), fit.height.toInt()),
        )
        val boxRadius = CornerRadius(BoxRadius.toPx())
        val chipPaddingX = ChipPaddingX.toPx()
        val chipPaddingY = ChipPaddingY.toPx()
        val dotDiameter = DotSize.toPx()
        val dotGap = DotGap.toPx()
        annotated.result.detections.forEachIndexed { index, detection ->
            val box = detection.box
            val left = fit.left + box.left * fit.width
            val top = fit.top + box.top * fit.height
            val boxSize = Size(box.width * fit.width, box.height * fit.height)
            val argb = OverlayPalette.colorOf(detection.label.value)
            val color = Color(argb)
            drawRoundRect(color.copy(alpha = GLOW_ALPHA), Offset(left, top), boxSize, boxRadius, glowStroke)
            drawRoundRect(color, Offset(left, top), boxSize, boxRadius, boxStroke)
            if (index < MAX_LABELS) {
                val layout = labels.layoutOf(detection.label, (detection.score * PERCENT).toInt())
                val chipWidth = dotDiameter + dotGap + layout.size.width + 2 * chipPaddingX
                val chipHeight = layout.size.height + 2 * chipPaddingY
                val at = placeChip(left, top, top + boxSize.height, chipWidth, chipHeight, fit)
                drawRoundRect(
                    ChipFill,
                    Offset(at.left, at.top),
                    Size(chipWidth, chipHeight),
                    CornerRadius(chipHeight / 2),
                )
                drawCircle(
                    color,
                    dotDiameter / 2,
                    Offset(at.left + chipPaddingX + dotDiameter / 2, at.top + chipHeight / 2),
                )
                drawText(
                    layout,
                    color = Color.White,
                    topLeft = Offset(at.left + chipPaddingX + dotDiameter + dotGap, at.top + chipPaddingY),
                )
            }
        }
        onDraw(annotated)
    }
}

/** Measured label text, keyed by (class, percent) so no string is built for a label already seen. */
private class LabelLayouts(
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
