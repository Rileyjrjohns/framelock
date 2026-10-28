package dev.framelock.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.framelock.designsystem.GlassPalette
import dev.framelock.designsystem.Spacing
import dev.framelock.designsystem.Size as ComponentSize

@Composable
internal fun Separator() {
    Spacer(
        Modifier
            .padding(vertical = Spacing.Minimal)
            .fillMaxWidth()
            .height(ComponentSize.Edge)
            .background(Color(GlassPalette.BORDER)),
    )
}
