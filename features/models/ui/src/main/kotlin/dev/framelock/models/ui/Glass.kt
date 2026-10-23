@file:Suppress("ktlint:compose:compositionlocal-allowlist")

package dev.framelock.models.ui

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

/*
 * The glass primitives of the Models screen. They are a small copy of `live:ui`'s (Glass.kt, GlassPalette.kt): a UI
 * module may not see another feature's UI module (ADR-014), and a shared UI module has not been decided on. Unlike the
 * live screen, the backdrop here is the app's own dark gradient and cards, never a picture, so the panels need only the
 * 10% white tint, not the extra shade that keeps text readable over a white video.
 */

internal val PanelShape = RoundedCornerShape(28.dp)

/** Smallest touch target (docs/architecture.md, "UI"). */
internal val TouchTarget = 64.dp

private val ShadowElevation = 24.dp
private val BlurRadius = 28.dp
private val ButtonPadding = 20.dp
private val EdgeWidth = 1.dp
private const val PRESSED_SCALE = 0.96f
private const val DISABLED_ALPHA = 0.4f

internal val Background = Color(0xFF0A0C10)
internal val Indigo = Color(0xFF1E1B4B)
internal val TextPrimary = Color(0xF2FFFFFF)
internal val TextSecondary = Color(0xB3FFFFFF)
internal val Tint = Color(0x1AFFFFFF)
internal val Edge = Color(0x24FFFFFF)
internal val DangerFill = Color(0x38F87171)

/** The accent's cyan end, for small icons that mark an active state. */
internal val AccentCyan = Color(0xFF22D3EE)

/** App background: the gradient the idle live screen uses, from the top. */
internal val BackdropBrush: Brush = Brush.verticalGradient(listOf(Indigo, Background))

/** Cyan to violet: the primary action and the selected states. */
internal val AccentBrush: Brush = Brush.horizontalGradient(listOf(Color(0xFF22D3EE), Color(0xFF8B5CF6)))

/** Dark text and icons on the gradient (AA on the whole gradient, see `live:ui`'s GlassPaletteTest). */
internal val OnAccent = Background

/** The state the glass panels blur; null (previews) makes a panel a flat translucent fill. */
internal val LocalGlassHaze = staticCompositionLocalOf<HazeState?> { null }

private val GlassBlurStyle =
    HazeBlurStyle {
        blurRadius(BlurRadius)
        noiseFactor(0f)
        colorEffects(listOf(HazeColorEffect.tint(Tint)))
    }

/** Blur needs RenderEffect (API 31); below that the panel is a flat translucent fill. */
private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The fill where there is no blur: the dark backdrop with white 10% over it, nearly opaque. */
private val FlatFill = Color(0xE6181A22)

/** A pane of frosted glass: what is behind it blurred and tinted when [blur] allows, with a 1 dp edge and a shadow. */
@Composable
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PanelShape,
    blur: Boolean = true,
    elevated: Boolean = true,
    fill: Color = FlatFill,
    content: @Composable BoxScope.() -> Unit,
) {
    val haze = LocalGlassHaze.current
    val input = remember(haze) { haze?.let { HazeInput.Sources(it) } }
    val shadowColor = Color(0x66000000)
    var panel = modifier
    if (elevated) panel = panel.shadow(ShadowElevation, shape, ambientColor = shadowColor, spotColor = shadowColor)
    panel = panel.clip(shape)
    panel =
        if (blur && BlurSupported && input != null) {
            panel.hazeBlur(input, GlassBlurStyle)
        } else {
            panel.background(fill)
        }
    Box(panel.border(EdgeWidth, Edge, shape), content = content)
}

/** How a [GlassButton] looks. */
internal enum class ButtonLook { Glass, Accent }

/** A glass or gradient button: scales to 96% with a spring while pressed, dims to 40% when disabled. */
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
    val fill: Brush = if (look == ButtonLook.Accent) AccentBrush else SolidColor(Tint)
    val contentColor = if (look == ButtonLook.Accent) OnAccent else TextPrimary
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else DISABLED_ALPHA
            }.clip(shape)
            .background(fill)
            .border(EdgeWidth, Edge, shape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}
