package dev.framelock.live.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.framelock.designsystem.EmptyState
import dev.framelock.live.ui.R
import dev.framelock.designsystem.R as DesignR

/** Shown while stopped with nothing to draw, so the screen is never an empty void: a glass card over the gradient. */
@Composable
internal fun IdleHint(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    EmptyState(
        stringResource(R.string.idle_title),
        stringResource(R.string.idle_hint),
        DesignR.drawable.ic_videocam,
        modifier,
        compact,
    )
}
