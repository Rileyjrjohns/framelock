package dev.framelock.models.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.framelock.designsystem.AppTopBar
import dev.framelock.designsystem.Spacing
import dev.framelock.models.ui.R

/** A glass bar with the back button and the title, floating over the top of the list. */
@Composable
internal fun TopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTopBar(
        stringResource(R.string.models_title),
        stringResource(R.string.action_back),
        onBack,
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            ).padding(Spacing.Medium),
    )
}
