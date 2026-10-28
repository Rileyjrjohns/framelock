package dev.framelock.designsystem

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** A source-style menu with full-size rows. */
@Composable
fun <T> AppDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemModifier: Modifier = Modifier,
) {
    DropdownMenu(expanded, onDismiss, modifier, shape = RoundedCornerShape(Radius.Menu)) {
        items.forEach { item ->
            DropdownMenuItem(
                text = { Text(label(item), style = MaterialTheme.typography.bodyLarge) },
                onClick = { onSelect(item) },
                modifier = itemModifier.heightIn(min = TouchTarget),
            )
        }
    }
}

@Preview
@Composable
private fun AppDropdownPreview() {
    FramelockTheme { AppDropdown(true, {}, listOf(stringResource(R.string.designsystem_catalog_action)), { it }, {}) }
}
