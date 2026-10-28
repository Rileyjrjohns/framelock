package dev.framelock.live.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.framelock.live.domain.pipeline.DegradationCause
import dev.framelock.live.ui.R

@Composable
internal fun degradationCauseText(value: DegradationCause): String =
    stringResource(
        when (value) {
            DegradationCause.THERMAL -> R.string.live_main_cause_thermal
            DegradationCause.MEMORY -> R.string.live_main_cause_memory
            DegradationCause.THERMAL_AND_MEMORY -> R.string.live_main_cause_thermal_and_memory
            DegradationCause.ENGINE_ERROR -> R.string.live_main_cause_engine_error
        },
    )
