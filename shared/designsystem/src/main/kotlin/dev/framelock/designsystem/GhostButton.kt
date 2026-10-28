package dev.framelock.designsystem

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp

/** The neutral glass action. */
@Composable
fun GhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    contentPadding: Dp = Spacing.Large,
    content: @Composable RowScope.() -> Unit,
) {
    BaseButton(onClick, modifier, enabled, shape, ButtonLook.Glass, contentPadding, content)
}

@Preview
@Composable
private fun GhostButtonPreview() {
    FramelockTheme { GhostButton({}) { Text(stringResource(R.string.designsystem_catalog_action)) } }
}
