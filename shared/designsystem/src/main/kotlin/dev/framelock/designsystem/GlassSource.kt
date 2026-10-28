package dev.framelock.designsystem

import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** Records the content behind sibling glass panels. */
fun Modifier.glassSource(state: HazeState): Modifier = hazeSource(state)
