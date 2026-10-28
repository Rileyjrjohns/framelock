package dev.framelock.live.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.framelock.live.domain.pipeline.PauseCause
import dev.framelock.live.ui.R

@Composable
internal fun pauseCauseText(value: PauseCause): String =
    stringResource(
        when (value) {
            PauseCause.THERMAL -> R.string.live_main_cause_thermal
            PauseCause.MEMORY -> R.string.live_main_cause_memory
            PauseCause.THERMAL_AND_MEMORY -> R.string.live_main_cause_thermal_and_memory
        },
    )
