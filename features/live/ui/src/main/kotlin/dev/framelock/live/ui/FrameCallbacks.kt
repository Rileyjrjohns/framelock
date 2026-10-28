package dev.framelock.live.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import dev.framelock.live.application.pipeline.AnnotatedFrame
import dev.framelock.live.domain.inference.LabelId

/** What the analysed frame needs from the view model: its pixels, the label names, and the report that it was drawn. */
@Immutable
class FrameCallbacks(
    val bitmapFor: (AnnotatedFrame, Int, Int) -> Bitmap?,
    val labelOf: (LabelId) -> String,
    val onDraw: (AnnotatedFrame) -> Unit,
)
