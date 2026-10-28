package dev.framelock.designsystem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

@Composable
internal fun BaseCard(
    modifier: Modifier = Modifier,
    shape: Shape = PanelShape,
    blur: Boolean = true,
    elevated: Boolean = true,
    fill: Color? = null,
    edge: Brush = EdgeBrush,
    edgeWidth: Dp = Size.Edge,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LocalGlassStyle.current
    val haze = LocalGlassHaze.current
    val input = remember(haze) { haze?.let { HazeInput.Sources(it) } }
    val shadowColor = Color(GlassPalette.SHADOW)
    var panel = modifier
    if (elevated) panel = panel.shadow(Elevation.Panel, shape, ambientColor = shadowColor, spotColor = shadowColor)
    panel = panel.clip(shape)
    panel =
        if (blur && BlurSupported && input != null) {
            panel.hazeBlur(input, if (style == GlassStyle.Video) VideoBlurStyle else AppBlurStyle)
        } else {
            panel.background(
                fill ?: Color(if (style == GlassStyle.Video) GlassPalette.FLAT_FILL else GlassPalette.APP_FLAT_FILL),
            )
        }
    Box(panel.border(edgeWidth, edge, shape), content = content)
}

private val VideoBlurStyle =
    HazeBlurStyle {
        blurRadius(Radius.Panel)
        noiseFactor(0f)
        colorEffects(listOf(HazeColorEffect.tint(Color(GlassPalette.VIDEO_SHADE)), HazeColorEffect.tint(Tint)))
    }
private val AppBlurStyle =
    HazeBlurStyle {
        blurRadius(Radius.Panel)
        noiseFactor(0f)
        colorEffects(listOf(HazeColorEffect.tint(Tint)))
    }
private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
