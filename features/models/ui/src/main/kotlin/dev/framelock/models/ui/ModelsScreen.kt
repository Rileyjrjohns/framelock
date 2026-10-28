package dev.framelock.models.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.rememberHazeState
import dev.framelock.designsystem.BackdropBrush
import dev.framelock.designsystem.GlassBackdrop
import dev.framelock.designsystem.GlassStyle
import dev.framelock.designsystem.Spacing
import dev.framelock.designsystem.glassSource
import dev.framelock.models.ui.components.ModelActionsBar
import dev.framelock.models.ui.components.ModelCard
import dev.framelock.models.ui.components.ModelsEmpty
import dev.framelock.models.ui.components.StatusCard
import dev.framelock.models.ui.components.TopBar
import dev.framelock.models.ui.components.statusText
import dev.framelock.designsystem.Size as ComponentSize

@Composable
fun ModelsScreen(
    ui: ModelsUiState,
    actions: ModelsScreenActions,
    modifier: Modifier = Modifier,
) {
    val canSwap = ui.canSwap
    val enabled = canSwap && !ui.busy
    val haze = rememberHazeState()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val status = statusText(ui, canSwap)
    GlassBackdrop(haze, GlassStyle.App) {
        Box(modifier.fillMaxSize().background(BackdropBrush)) {
            // The list is what the top bar and the dock blur as it scrolls under them.
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .glassSource(
                        haze,
                    ).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentPadding =
                    PaddingValues(
                        start = Spacing.Standard,
                        end = Spacing.Standard,
                        top =
                            insets.calculateTopPadding() + Spacing.Medium + ComponentSize.ModelsTopBar + Spacing.Medium,
                        bottom =
                            insets.calculateBottomPadding() +
                                ComponentSize.ModelsDock + Spacing.Medium + Spacing.Medium,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                status?.let { (text, refused) -> item { StatusCard(text, refused) } }
                if (ui.rows.isEmpty()) item { ModelsEmpty() }
                items(ui.rows, key = { it.version.value }) { row ->
                    ModelCard(row, enabled, onActivate = { actions.onActivate(row.version) })
                }
            }
            TopBar(actions.onBack, Modifier.align(Alignment.TopCenter))
            ModelActionsBar(
                enabled,
                ui.canRollBack,
                actions.onRollback,
                actions.onImport,
                Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
