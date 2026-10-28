package dev.framelock.live.ui.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.framelock.designsystem.AppColors
import dev.framelock.designsystem.OverlayPalette
import dev.framelock.designsystem.Radius
import dev.framelock.designsystem.Spacing
import dev.framelock.designsystem.TABULAR_NUMBERS
import dev.framelock.live.application.pipeline.AnnotatedFrame
import dev.framelock.live.domain.inference.LabelId
import dev.framelock.live.ui.ChipFill
import dev.framelock.live.ui.GLOW_ALPHA
import dev.framelock.live.ui.LabelLayouts
import dev.framelock.live.ui.MAX_LABELS
import dev.framelock.live.ui.PERCENT
import dev.framelock.live.ui.R
import dev.framelock.live.ui.fitInto
import dev.framelock.live.ui.placeChip
import dev.framelock.designsystem.Size as ComponentSize

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
    bitmapFor: (AnnotatedFrame, Int, Int) -> Bitmap?,
    labelOf: (LabelId) -> String,
    onDraw: (AnnotatedFrame) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style =
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR_NUMBERS)
    val pattern = stringResource(R.string.live_main_detection_label)
    val labels = remember(labelOf, style, density, pattern) { LabelLayouts(measurer, style, labelOf, pattern) }
    val (boxStroke, glowStroke) =
        remember(
            density,
        ) { with(density) { Stroke(ComponentSize.DetectionStroke.toPx()) to Stroke(Spacing.Small.toPx()) } }
    Canvas(modifier.fillMaxSize()) {
        val fit = fitInto(size.width, size.height, annotated.width.toFloat(), annotated.height.toFloat())
        val bitmap = bitmapFor(annotated, fit.width.toInt(), fit.height.toInt()) ?: return@Canvas
        drawImage(
            bitmap.asImageBitmap(),
            dstOffset = IntOffset(fit.left.toInt(), fit.top.toInt()),
            dstSize = IntSize(fit.width.toInt(), fit.height.toInt()),
        )
        val boxRadius = CornerRadius(Radius.Detection.toPx())
        val chipPaddingX = Spacing.Intermediate.toPx()
        val chipPaddingY = ComponentSize.LabelPaddingY.toPx()
        val dotDiameter = Spacing.Compact.toPx()
        val dotGap = Spacing.Small.toPx()
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
                    color = AppColors.White,
                    topLeft = Offset(at.left + chipPaddingX + dotDiameter + dotGap, at.top + chipPaddingY),
                )
            }
        }
        onDraw(annotated)
    }
}
