package dev.framelock.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview

/** A ready or empty-list card with a gradient icon and supporting text. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    panel: Boolean = false,
) {
    BaseEmptyState(title, message, icon, modifier, compact, panel)
}

@Composable
internal fun BaseEmptyState(
    title: String,
    message: String,
    icon: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    panel: Boolean = false,
) {
    val card =
        if (panel) {
            modifier.fillMaxWidth()
        } else {
            modifier
                .padding(
                    horizontal = Spacing.Spacious,
                ).widthIn(max = Size.IdleCard)
        }
    AppCard(card, blur = !panel, elevated = !panel, fill = if (panel) Tint else null) {
        Column(
            if (panel) {
                Modifier.fillMaxWidth().padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.EmptyPanel)
            } else {
                Modifier.padding(
                    horizontal = Spacing.Generous,
                    vertical = if (compact) Spacing.Large else Spacing.Spacious,
                )
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            if (!compact) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(Size.EmptyIcon)
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                drawRect(AccentBrush, blendMode = BlendMode.SrcIn)
                            },
                    tint = AppColors.White,
                )
            }
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun EmptyStatePreview() {
    FramelockTheme {
        EmptyState(
            stringResource(R.string.designsystem_catalog_title),
            stringResource(R.string.designsystem_catalog_message),
            R.drawable.ic_videocam,
        )
    }
}
