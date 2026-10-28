package dev.framelock.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp

@Composable
internal fun BaseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    look: ButtonLook = ButtonLook.Glass,
    contentPadding: Dp = Spacing.Large,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) Motion.PRESSED_SCALE else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "press",
    )
    val glassFill = SolidColor(Color(GlassPalette.TINT))
    val (fill, edge, ringWidth, contentColor) =
        when (look) {
            ButtonLook.Glass -> {
                ButtonPaint(glassFill, EdgeBrush, Size.Edge, GlassPalette.TEXT_PRIMARY)
            }

            ButtonLook.Accent -> {
                ButtonPaint(AccentBrush, EdgeBrush, Size.Edge, GlassPalette.ON_ACCENT)
            }

            ButtonLook.Danger -> {
                ButtonPaint(
                    SolidColor(Color(GlassPalette.DANGER_FILL)),
                    DangerEdgeBrush,
                    Size.Edge,
                    GlassPalette.TEXT_PRIMARY,
                )
            }

            ButtonLook.Selected -> {
                ButtonPaint(glassFill, AccentBrush, Size.Ring, GlassPalette.TEXT_PRIMARY)
            }
        }
    Row(
        modifier
            .sizeIn(minWidth = TouchTarget, minHeight = TouchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else Motion.DISABLED_ALPHA
            }.clip(shape)
            .background(fill)
            .border(ringWidth, edge, shape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color(contentColor)) { content() }
    }
}

private data class ButtonPaint(
    val fill: Brush,
    val edge: Brush,
    val edgeWidth: Dp,
    val content: Int,
)
