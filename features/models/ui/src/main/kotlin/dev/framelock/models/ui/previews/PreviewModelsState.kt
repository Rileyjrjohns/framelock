package dev.framelock.models.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.models.ui.ModelRow
import dev.framelock.models.ui.ModelsUiState
import dev.framelock.models.ui.R

@Composable
internal fun previewModelsState(
    canSwap: Boolean = true,
    busy: Boolean = false,
) = ModelsUiState(
    rows =
        listOf(
            ModelRow(ModelVersion(stringResource(R.string.models_preview_current)), true, false),
            ModelRow(ModelVersion(stringResource(R.string.models_preview_previous)), false, true),
            ModelRow(ModelVersion(stringResource(R.string.models_preview_other)), false, false),
        ),
    busy = busy,
    canSwap = canSwap,
)
