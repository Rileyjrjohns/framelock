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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val CardMaxWidth = 420.dp

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
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(Modifier.widthIn(max = CardMaxWidth)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    message,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Start,
                )
                GlassButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
                    look = ButtonLook.Accent,
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

@Preview(widthDp = 400, heightDp = 600)
@Composable
private fun PermissionPromptPreview() {
    PermissionPrompt("The camera is needed to analyse the video feed.", "Allow camera", {})
}
