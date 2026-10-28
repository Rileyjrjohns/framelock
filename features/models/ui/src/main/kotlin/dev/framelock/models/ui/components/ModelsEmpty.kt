package dev.framelock.models.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.framelock.designsystem.EmptyState
import dev.framelock.models.ui.R
import dev.framelock.designsystem.R as DesignR

@Composable
internal fun ModelsEmpty() {
    EmptyState(
        stringResource(R.string.models_main_empty),
        stringResource(R.string.models_main_empty_hint),
        DesignR.drawable.ic_upload_file,
        panel = true,
    )
}
