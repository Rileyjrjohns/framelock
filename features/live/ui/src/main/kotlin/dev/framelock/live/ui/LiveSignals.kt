package dev.framelock.live.ui

import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import kotlinx.coroutines.flow.StateFlow

class LiveSignals(
    val selectedSource: StateFlow<SourceOption>,
    val sourceDescription: StateFlow<String?>,
    val thermal: StateFlow<ThermalLevel>,
    val memory: StateFlow<MemoryPressure>,
    val backend: StateFlow<String?>,
)
