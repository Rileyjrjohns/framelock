@file:Suppress("ktlint:compose:compositionlocal-allowlist")

package dev.framelock.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState

val LocalGlassHaze = staticCompositionLocalOf<HazeState?> { null }
val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.Video }

/** Shares a blur source and the tint appropriate to its background. */
@Composable
fun GlassBackdrop(
    haze: HazeState,
    style: GlassStyle = GlassStyle.Video,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGlassHaze provides haze, LocalGlassStyle provides style, content = content)
}
