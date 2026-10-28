package dev.framelock.models.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.framelock.designsystem.StatusMessage

@Composable
internal fun StatusCard(
    text: String,
    refused: Boolean,
) {
    StatusMessage(text, refused, Modifier.fillMaxWidth(), panel = true)
}
