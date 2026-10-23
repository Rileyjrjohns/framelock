package dev.framelock.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one theme of the app: dark only, since the video is the content and the interface floats over it as frosted
 * glass. The glass panels draw their own colours (`live:ui` and `models:ui`, checked for AA contrast over a white
 * picture); this scheme is for the Material parts that remain (menus, text defaults) and the app background. The
 * semantic colours (status, detection classes) live next to the code that draws them.
 */
private val FramelockColors =
    darkColorScheme(
        primary = Color(0xFF22D3EE),
        onPrimary = Color(0xFF0A0C10),
        primaryContainer = Color(0xFF164E63),
        onPrimaryContainer = Color(0xFFCFFAFE),
        secondary = Color(0xFFA78BFA),
        onSecondary = Color(0xFF0A0C10),
        secondaryContainer = Color(0xFF3B2F6B),
        onSecondaryContainer = Color(0xFFEDE9FE),
        tertiary = Color(0xFF8B5CF6),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF3B2F6B),
        onTertiaryContainer = Color(0xFFEDE9FE),
        error = Color(0xFFF87171),
        onError = Color(0xFF0A0C10),
        errorContainer = Color(0xFF7F1D1D),
        onErrorContainer = Color(0xFFFEE2E2),
        background = Color(0xFF0A0C10),
        onBackground = Color(0xF2FFFFFF),
        surface = Color(0xFF0A0C10),
        onSurface = Color(0xF2FFFFFF),
        surfaceVariant = Color(0xFF1B1E26),
        onSurfaceVariant = Color(0xB3FFFFFF),
        surfaceContainerLowest = Color(0xFF07080B),
        surfaceContainerLow = Color(0xFF0F1116),
        surfaceContainer = Color(0xFF14171D),
        surfaceContainerHigh = Color(0xFF1B1E26),
        surfaceContainerHighest = Color(0xFF242832),
        outline = Color(0xFF8B919A),
        outlineVariant = Color(0x24FFFFFF),
        scrim = Color(0xFF000000),
    )

/** Inter (SIL OFL 1.1, docs/soup.md): four static weights, which Compose maps to the weights the styles ask for. */
private val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

private fun TextStyle.inter(
    size: Int,
    weight: FontWeight,
    line: Int,
) = copy(fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/** Display 28 SemiBold, title 20 SemiBold, body 16 Regular, label 14 Medium. Figures that change use `tnum` where drawn. */
private val FramelockTypography =
    Typography().run {
        copy(
            displayLarge = displayLarge.inter(57, FontWeight.SemiBold, 64),
            displayMedium = displayMedium.inter(45, FontWeight.SemiBold, 52),
            displaySmall = displaySmall.inter(28, FontWeight.SemiBold, 36),
            headlineLarge = headlineLarge.inter(32, FontWeight.SemiBold, 40),
            headlineMedium = headlineMedium.inter(28, FontWeight.SemiBold, 36),
            headlineSmall = headlineSmall.inter(24, FontWeight.SemiBold, 32),
            titleLarge = titleLarge.inter(20, FontWeight.SemiBold, 28),
            titleMedium = titleMedium.inter(16, FontWeight.SemiBold, 24),
            titleSmall = titleSmall.inter(14, FontWeight.Medium, 20),
            bodyLarge = bodyLarge.inter(16, FontWeight.Normal, 24),
            bodyMedium = bodyMedium.inter(14, FontWeight.Normal, 20),
            bodySmall = bodySmall.inter(12, FontWeight.Normal, 16),
            labelLarge = labelLarge.inter(14, FontWeight.Medium, 20),
            labelMedium = labelMedium.inter(12, FontWeight.Medium, 16),
            labelSmall = labelSmall.inter(11, FontWeight.Medium, 16),
        )
    }

private val FramelockShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

@Composable
fun FramelockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FramelockColors,
        typography = FramelockTypography,
        shapes = FramelockShapes,
        content = content,
    )
}
