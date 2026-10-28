package dev.framelock.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview

/** A semantic state pill with an optional pulsing running indicator. */
@Composable
fun StatusChip(
    text: String,
    argb: Int,
    modifier: Modifier = Modifier,
    running: Boolean = false,
) {
    val fill by animateColorAsState(Color(argb), tween(Motion.SWAP_MILLIS), label = "state colour")
    val onState = Color(StateColors.ON_STATE)
    BaseChip(
        modifier =
            modifier
                .clip(
                    CircleShape,
                ).background(fill)
                .padding(horizontal = Spacing.Standard, vertical = Spacing.Intermediate),
        gap = Spacing.Compact,
    ) {
        if (running) PulsingDot(onState)
        Text(
            text,
            color = onState,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        Motion.PULSE_LOW,
        1f,
        infiniteRepeatable(tween(Motion.PULSE_MILLIS), RepeatMode.Reverse),
        label = "pulse alpha",
    )
    Box(
        Modifier
            .size(Size.StatusDot)
            .graphicsLayer { this.alpha = alpha }
            .background(color, CircleShape),
    )
}

@Preview
@Composable
private fun StatusChipPreview() {
    FramelockTheme {
        StatusChip(stringResource(R.string.designsystem_catalog_running), StateColors.RUNNING, running = true)
    }
}
