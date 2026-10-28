package dev.framelock.live.ui

import androidx.compose.runtime.Immutable
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel

/** What the debug HUD shows; null while it is hidden. */
@Immutable
data class HudState(
    val sourceDescription: String?,
    val backend: String?,
    val thermal: ThermalLevel,
    val memory: MemoryPressure,
)
