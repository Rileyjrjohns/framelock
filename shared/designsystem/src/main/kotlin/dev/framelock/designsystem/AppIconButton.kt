package dev.framelock.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** A round glass action, optionally ringed with the accent gradient. */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    icon: Int,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    BaseButton(
        onClick,
        modifier.size(TouchTarget),
        enabled,
        look = if (selected) ButtonLook.Selected else ButtonLook.Glass,
        contentPadding = 0.dp,
    ) {
        Icon(painterResource(icon), description, Modifier.size(Size.ActionIcon))
    }
}

@Preview
@Composable
private fun AppIconButtonPreview() {
    FramelockTheme {
        AppIconButton({}, R.drawable.ic_videocam, stringResource(R.string.designsystem_catalog_action), selected = true)
    }
}
