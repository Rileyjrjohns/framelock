package dev.framelock.live.ui

import androidx.compose.ui.graphics.Color
import dev.framelock.designsystem.GlassPalette

internal const val MAX_LABELS = 5

internal const val PERCENT = 100

/** Past this many distinct (class, percent) labels the text cache starts over: it only guards against growth. */
internal const val MAX_CACHED_LABELS = 512

internal const val GLOW_ALPHA = 0.35f

internal val ChipFill = Color(GlassPalette.CHIP_FILL)
