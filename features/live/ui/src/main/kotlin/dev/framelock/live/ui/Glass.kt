@file:Suppress("ktlint:compose:compositionlocal-allowlist")

package dev.framelock.live.ui

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/** Panels: top bar, HUD, dock, cards. */
internal val PanelShape = RoundedCornerShape(28.dp)

/** Smallest touch target (docs/architecture.md, "UI"). */
internal val TouchTarget = 64.dp

private val PanelShadowElevation = 24.dp
private val BlurRadius = 28.dp
private val ButtonPadding = 20.dp
private val RingWidth = 2.dp
private val EdgeWidth = 1.dp
private const val PRESSED_SCALE = 0.96f
private const val DISABLED_ALPHA = 0.4f

/**
 * The state the glass panels blur: the video area marks itself with `Modifier.hazeSource(state)`. Null (previews, the
 * landscape rail) makes a panel a flat translucent fill.
 */
internal val LocalGlassHaze = staticCompositionLocalOf<HazeState?> { null }

/** Cyan to violet: the primary action and the selected states. */
internal val AccentBrush: Brush =
    Brush.horizontalGradient(listOf(Color(GlassPalette.ACCENT_START), Color(GlassPalette.ACCENT_END)))

/** White at 95% and 70%, over glass. */
internal val TextPrimary = Color(GlassPalette.TEXT_PRIMARY)
internal val TextSecondary = Color(GlassPalette.TEXT_SECONDARY)

/** OpenType feature that gives digits a fixed width, so values do not jump around as they change. */
internal const val TABULAR_NUMBERS = "tnum"

private val EdgeBrush: Brush = SolidColor(Color(GlassPalette.BORDER))
private val DangerEdgeBrush: Brush = SolidColor(Color(GlassPalette.DANGER_BORDER))

private val GlassBlurStyle =
    HazeBlurStyle {
        blurRadius(BlurRadius)
        noiseFactor(0f)
        colorEffects(
            listOf(
                HazeColorEffect.tint(Color(GlassPalette.VIDEO_SHADE)),
                HazeColorEffect.tint(Color(GlassPalette.TINT)),
            ),
        )
    }

/**
 * Blur needs RenderEffect (API 31). Below that this draws its own flat translucent fill: Haze's own fallback raises the
 * tint's alpha with the blur radius until the scrim is nearly opaque, which is not the look wanted here.
 */
private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * A pane of frosted glass: the source behind it blurred (when [blur] and a [LocalGlassHaze] exist, API 31+), shaded
 * and tinted, with a 1 dp edge and a soft shadow. Colours are in [GlassPalette], checked for AA over a white picture.
 */
@Composable
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PanelShape,
    blur: Boolean = true,
    elevated: Boolean = true,
    fill: Color = Color(GlassPalette.FLAT_FILL),
    edge: Brush = EdgeBrush,
    edgeWidth: Dp = EdgeWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    val haze = LocalGlassHaze.current
    val input = remember(haze) { haze?.let { HazeInput.Sources(it) } }
    val shadowColor = Color(GlassPalette.SHADOW)
    var panel = modifier
    if (elevated) panel = panel.shadow(PanelShadowElevation, shape, ambientColor = shadowColor, spotColor = shadowColor)
    panel = panel.clip(shape)
    panel =
        if (blur && BlurSupported && input != null) {
            panel.hazeBlur(input, GlassBlurStyle)
        } else {
            panel.background(fill)
        }
    Box(panel.border(edgeWidth, edge, shape), content = content)
}

/** How a [GlassButton] looks. */
internal enum class ButtonLook { Glass, Accent, Danger, Selected }

/**
 * A button of glass, at least as large as the caller makes it (use [TouchTarget]). Pressing scales it to 96% with a
 * spring; disabled it dims to 40%. [Accent][ButtonLook.Accent] is the gradient with dark content; [Danger][ButtonLook.Danger]
 * a red tint and edge; [Selected][ButtonLook.Selected] glass ringed with the gradient.
 */
@Composable
internal fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    look: ButtonLook = ButtonLook.Glass,
    contentPadding: Dp = ButtonPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) PRESSED_SCALE else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "press",
    )
    val glassFill = SolidColor(Color(GlassPalette.TINT))
    val (fill, edge, ringWidth, contentColor) =
        when (look) {
            ButtonLook.Glass -> {
                ButtonPaint(glassFill, EdgeBrush, EdgeWidth, GlassPalette.TEXT_PRIMARY)
            }

            ButtonLook.Accent -> {
                ButtonPaint(AccentBrush, EdgeBrush, EdgeWidth, GlassPalette.ON_ACCENT)
            }

            ButtonLook.Danger -> {
                ButtonPaint(
                    SolidColor(Color(GlassPalette.DANGER_FILL)),
                    DangerEdgeBrush,
                    EdgeWidth,
                    GlassPalette.TEXT_PRIMARY,
                )
            }

            ButtonLook.Selected -> {
                ButtonPaint(glassFill, AccentBrush, RingWidth, GlassPalette.TEXT_PRIMARY)
            }
        }
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else DISABLED_ALPHA
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
