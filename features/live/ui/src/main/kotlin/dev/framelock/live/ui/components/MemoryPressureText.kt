package dev.framelock.live.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.ui.R

@Composable
internal fun memoryPressureText(value: MemoryPressure): String =
    stringResource(
        when (value) {
            MemoryPressure.NORMAL -> R.string.live_main_memory_normal
            MemoryPressure.MODERATE -> R.string.live_main_memory_moderate
            MemoryPressure.CRITICAL -> R.string.live_main_memory_critical
        },
    )
