package dev.framelock.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

val PanelShape = RoundedCornerShape(Radius.Panel)
val TouchTarget = Size.TouchTarget
val TextPrimary = Color(GlassPalette.TEXT_PRIMARY)
val TextSecondary = Color(GlassPalette.TEXT_SECONDARY)
val Background = Color(GlassPalette.BACKGROUND)
val Indigo = Color(GlassPalette.INDIGO)
val Tint = Color(GlassPalette.TINT)
val Edge = Color(GlassPalette.BORDER)
val DangerFill = Color(GlassPalette.DANGER_FILL)
val AccentCyan = Color(GlassPalette.ACCENT_START)
val OnAccent = Background
val AccentBrush: Brush = Brush.horizontalGradient(listOf(AccentCyan, Color(GlassPalette.ACCENT_END)))
val BackdropBrush: Brush = Brush.verticalGradient(listOf(Indigo, Background))
val IdleBrush: Brush = Brush.radialGradient(listOf(Indigo, Background))
const val TABULAR_NUMBERS = "tnum"
internal val EdgeBrush: Brush = SolidColor(Edge)
internal val DangerEdgeBrush: Brush = SolidColor(Color(GlassPalette.DANGER_BORDER))
