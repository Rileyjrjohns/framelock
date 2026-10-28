package dev.framelock.live.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.framelock.designsystem.AppCard
import dev.framelock.designsystem.IdleBrush
import dev.framelock.designsystem.PrimaryButton
import dev.framelock.designsystem.Spacing
import dev.framelock.designsystem.TextPrimary
import dev.framelock.designsystem.TouchTarget
import dev.framelock.designsystem.Size as ComponentSize

/**
 * What the app shows instead of the live screen while a permission is missing: a centred glass card on the app
 * gradient, an explanation and one accent button. The texts belong to the caller, which owns the permission.
 */
@Composable
fun PermissionPrompt(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(IdleBrush)
            .safeDrawingPadding()
            .padding(Spacing.ExtraLarge),
        contentAlignment = Alignment.Center,
    ) {
        AppCard(Modifier.widthIn(max = ComponentSize.PermissionCard)) {
            Column(Modifier.padding(Spacing.ExtraLarge), verticalArrangement = Arrangement.spacedBy(Spacing.Large)) {
                Text(
                    message,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Start,
                )
                PrimaryButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
                ) {
                    Text(
                        actionLabel,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}
