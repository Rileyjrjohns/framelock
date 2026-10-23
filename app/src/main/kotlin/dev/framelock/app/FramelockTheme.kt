package dev.framelock.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one theme of the app: dark only, since the video is the content and the interface floats over it. Every text and
 * container pair below reaches WCAG AA (most reach AAA) by construction of the tonal palette; the semantic colours
 * (status, detection classes) live next to the code that draws them.
 */
private val FramelockColors =
    darkColorScheme(
        primary = Color(0xFF9FCAFF),
        onPrimary = Color(0xFF00315C),
        primaryContainer = Color(0xFF1F4B7A),
        onPrimaryContainer = Color(0xFFD3E4FF),
        secondary = Color(0xFFBBC7DB),
        onSecondary = Color(0xFF253140),
        secondaryContainer = Color(0xFF3C4858),
        onSecondaryContainer = Color(0xFFD7E3F8),
        tertiary = Color(0xFFD6BEE4),
        onTertiary = Color(0xFF3B2948),
        tertiaryContainer = Color(0xFF523F5F),
        onTertiaryContainer = Color(0xFFF2DAFF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF101418),
        onBackground = Color(0xFFE1E3E8),
        surface = Color(0xFF101418),
        onSurface = Color(0xFFE1E3E8),
        surfaceVariant = Color(0xFF41474D),
        onSurfaceVariant = Color(0xFFC1C7CF),
        surfaceContainerLowest = Color(0xFF0B0F13),
        surfaceContainerLow = Color(0xFF181C20),
        surfaceContainer = Color(0xFF1C2024),
        surfaceContainerHigh = Color(0xFF262A2F),
        surfaceContainerHighest = Color(0xFF31353A),
        outline = Color(0xFF8B919A),
        outlineVariant = Color(0xFF41474D),
        scrim = Color(0xFF000000),
    )

/** Larger and bolder than the defaults where the app is read at a glance. */
private val FramelockTypography =
    Typography().run {
        copy(
            titleLarge = titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
            labelLarge = labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            labelMedium = labelMedium.copy(fontSize = 14.sp),
            bodyLarge = bodyLarge.copy(fontSize = 18.sp),
            headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        )
    }

private val FramelockShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(20.dp),
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
