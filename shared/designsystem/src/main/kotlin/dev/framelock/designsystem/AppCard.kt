package dev.framelock.designsystem

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp

/** A glass panel, with video-safe or app-background tint from GlassBackdrop. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = PanelShape,
    blur: Boolean = true,
    elevated: Boolean = true,
    fill: Color? = null,
    edge: Brush = EdgeBrush,
    edgeWidth: Dp = Size.Edge,
    content: @Composable BoxScope.() -> Unit,
) {
    BaseCard(modifier, shape, blur, elevated, fill, edge, edgeWidth, content)
}

@Preview
@Composable
private fun AppCardPreview() {
    FramelockTheme {
        AppCard { Text(stringResource(R.string.designsystem_catalog_title), Modifier.padding(Spacing.Large)) }
    }
}
