package dev.framelock.live.inference.litert

import dev.framelock.live.domain.inference.LabelId
import dev.framelock.live.domain.inference.NormalizedRect

internal data class ReferenceDetection(
    val label: LabelId,
    val box: NormalizedRect,
)
