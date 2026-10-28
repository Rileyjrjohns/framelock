package dev.framelock.live.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.framelock.live.domain.system.ThermalLevel
import dev.framelock.live.ui.R

@Composable
internal fun thermalLevelText(value: ThermalLevel): String =
    stringResource(
        when (value) {
            ThermalLevel.NONE -> R.string.live_main_thermal_none
            ThermalLevel.LIGHT -> R.string.live_main_thermal_light
            ThermalLevel.MODERATE -> R.string.live_main_thermal_moderate
            ThermalLevel.SEVERE -> R.string.live_main_thermal_severe
            ThermalLevel.CRITICAL -> R.string.live_main_thermal_critical
            ThermalLevel.EMERGENCY -> R.string.live_main_thermal_emergency
            ThermalLevel.SHUTDOWN -> R.string.live_main_thermal_shutdown
        },
    )
