package dev.framelock.models.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.framelock.models.ui.ModelsOutcome
import dev.framelock.models.ui.R

@Composable
internal fun outcomeText(outcome: ModelsOutcome): String =
    when (outcome) {
        is ModelsOutcome.Imported -> {
            stringResource(R.string.models_main_outcome_imported, outcome.version.value)
        }

        is ModelsOutcome.Changed -> {
            outcome.from?.let { stringResource(R.string.models_main_outcome_changed, it.value, outcome.to.value) }
                ?: stringResource(R.string.models_main_outcome_changed_first, outcome.to.value)
        }

        is ModelsOutcome.Unchanged -> {
            stringResource(R.string.models_main_outcome_unchanged, outcome.version.value)
        }

        is ModelsOutcome.Refused -> {
            stringResource(R.string.models_main_outcome_refused, stringResource(reasonText(outcome.reason)))
        }
    }
