package dev.framelock.models.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.framelock.models.ui.ModelsOutcome
import dev.framelock.models.ui.ModelsUiState
import dev.framelock.models.ui.R

/** Why the controls are off, or what the last action did; true when it is a refusal. */
@Composable
internal fun statusText(
    ui: ModelsUiState,
    canSwap: Boolean,
): Pair<String, Boolean>? =
    when {
        ui.busy -> stringResource(R.string.models_busy) to false
        !canSwap -> stringResource(R.string.models_locked) to false
        else -> ui.outcome?.let { outcomeText(it) to (it is ModelsOutcome.Refused) }
    }
