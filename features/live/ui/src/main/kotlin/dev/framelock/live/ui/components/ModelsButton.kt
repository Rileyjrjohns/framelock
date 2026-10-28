package dev.framelock.live.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.framelock.designsystem.AppIconButton
import dev.framelock.live.ui.R
import dev.framelock.designsystem.R as DesignR

@Composable
internal fun ModelsButton(onOpen: () -> Unit) {
    AppIconButton(onOpen, DesignR.drawable.ic_neurology, stringResource(R.string.live_main_action_models))
}
