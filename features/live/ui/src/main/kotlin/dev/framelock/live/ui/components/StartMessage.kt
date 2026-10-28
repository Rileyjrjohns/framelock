package dev.framelock.live.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.framelock.designsystem.StatusMessage
import dev.framelock.live.application.usecase.StartResult
import dev.framelock.live.ui.R

/** Why nothing is running: no model installed, or the active one did not load. */
@Composable
internal fun StartMessage(startResult: StartResult?) {
    val (text, error) =
        when (startResult) {
            StartResult.NoActiveModel -> {
                stringResource(R.string.start_no_model) to false
            }

            is StartResult.LoadFailed -> {
                val reason = startResult.cause.message ?: startResult.cause::class.simpleName.orEmpty()
                stringResource(R.string.start_load_failed, reason) to true
            }

            else -> {
                return
            }
        }
    StatusMessage(text, error, Modifier.fillMaxWidth())
}
