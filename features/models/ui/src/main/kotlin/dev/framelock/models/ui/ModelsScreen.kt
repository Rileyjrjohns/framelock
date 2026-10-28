package dev.framelock.models.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import dev.framelock.designsystem.AppCard
import dev.framelock.designsystem.AppTopBar
import dev.framelock.designsystem.BackdropBrush
import dev.framelock.designsystem.EmptyState
import dev.framelock.designsystem.GhostButton
import dev.framelock.designsystem.GlassStyle
import dev.framelock.designsystem.LocalGlassHaze
import dev.framelock.designsystem.LocalGlassStyle
import dev.framelock.designsystem.PrimaryButton
import dev.framelock.designsystem.SelectionChip
import dev.framelock.designsystem.StatusMessage
import dev.framelock.designsystem.TextPrimary
import dev.framelock.designsystem.Tint
import dev.framelock.designsystem.TouchTarget
import dev.framelock.designsystem.glassSource
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.designsystem.R as DesignR

/** `.flmodel` has no registered media type, so the picker is not narrowed. */
private val PickerMimeTypes = arrayOf("*/*")

/**
 * The Models screen: installed versions, activate, roll back and import. Every action is disabled while a swap is not
 * allowed ([ModelsViewModel.canSwap]) or another action is running.
 */
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val canSwap by viewModel.canSwap.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.import(UriModelSource(resolver, uri))
        }
    ModelsContent(
        ui = ui,
        canSwap = canSwap,
        onBack = onBack,
        onActivate = { version ->
            viewModel.track(ElementId.ACTIVATE_MODEL)
            viewModel.activate(version)
        },
        onRollback = {
            viewModel.track(ElementId.ROLLBACK_MODEL)
            viewModel.rollback()
        },
        onImport = {
            viewModel.track(ElementId.IMPORT_MODEL)
            picker.launch(PickerMimeTypes)
        },
        modifier = modifier,
    )
}

/** Space the floating top bar and bottom dock take, so the list can scroll clear of them. */
private val TopBarHeight = 80.dp
private val DockHeight = 96.dp
private val Margin = 12.dp

@Composable
private fun ModelsContent(
    ui: ModelsUiState,
    canSwap: Boolean,
    onBack: () -> Unit,
    onActivate: (ModelVersion) -> Unit,
    onRollback: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = canSwap && !ui.busy
    val haze = rememberHazeState()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val status = statusText(ui, canSwap)
    CompositionLocalProvider(LocalGlassHaze provides haze, LocalGlassStyle provides GlassStyle.App) {
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
                        start = 16.dp,
                        end = 16.dp,
                        top = insets.calculateTopPadding() + Margin + TopBarHeight + Margin,
                        bottom = insets.calculateBottomPadding() + Margin + DockHeight + Margin,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                status?.let { (text, refused) -> item { StatusCard(text, refused) } }
                if (ui.rows.isEmpty()) item { ModelsEmpty() }
                items(ui.rows, key = { it.version.value }) { row ->
                    ModelCard(row, enabled, onActivate = { onActivate(row.version) })
                }
            }
            TopBar(onBack, Modifier.align(Alignment.TopCenter))
            Actions(enabled, ui.canRollBack, onRollback, onImport, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** A glass bar with the back button and the title, floating over the top of the list. */
@Composable
private fun TopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTopBar(
        stringResource(R.string.models_title),
        stringResource(R.string.action_back),
        onBack,
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            ).padding(Margin),
    )
}

/** Why the controls are off, or what the last action did; true when it is a refusal. */
@Composable
private fun statusText(
    ui: ModelsUiState,
    canSwap: Boolean,
): Pair<String, Boolean>? =
    when {
        ui.busy -> stringResource(R.string.models_busy) to false
        !canSwap -> stringResource(R.string.models_locked) to false
        else -> ui.outcome?.let { outcomeText(it) to (it is ModelsOutcome.Refused) }
    }

@Composable
private fun StatusCard(
    text: String,
    refused: Boolean,
) {
    StatusMessage(text, refused, Modifier.fillMaxWidth(), panel = true)
}

@Composable
private fun ModelsEmpty() {
    EmptyState(
        stringResource(R.string.models_empty),
        stringResource(R.string.models_empty_hint),
        DesignR.drawable.ic_upload_file,
        panel = true,
    )
}

/** Roll back (glass) and import (gradient), always in reach in a dock at the bottom. */
@Composable
private fun Actions(
    enabled: Boolean,
    canRollBack: Boolean,
    onRollback: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(Margin),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton(
                onClick = onRollback,
                modifier = Modifier.weight(1f).heightIn(min = TouchTarget),
                enabled = enabled && canRollBack,
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_undo),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    stringResource(R.string.action_rollback),
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
            PrimaryButton(
                onClick = onImport,
                modifier = Modifier.weight(1f).heightIn(min = TouchTarget),
                enabled = enabled,
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_upload_file),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    stringResource(R.string.action_import),
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun outcomeText(outcome: ModelsOutcome): String =
    when (outcome) {
        is ModelsOutcome.Imported -> {
            stringResource(R.string.outcome_imported, outcome.version.value)
        }

        is ModelsOutcome.Changed -> {
            outcome.from?.let { stringResource(R.string.outcome_changed, it.value, outcome.to.value) }
                ?: stringResource(R.string.outcome_changed_first, outcome.to.value)
        }

        is ModelsOutcome.Unchanged -> {
            stringResource(R.string.outcome_unchanged, outcome.version.value)
        }

        is ModelsOutcome.Refused -> {
            stringResource(R.string.outcome_refused, stringResource(reasonText(outcome.reason)))
        }
    }

private fun reasonText(reason: ModelRejectionReason): Int =
    when (reason) {
        ModelRejectionReason.BAD_HASH -> R.string.reason_bad_hash
        ModelRejectionReason.BAD_SIGNATURE -> R.string.reason_bad_signature
        ModelRejectionReason.MALFORMED_MANIFEST -> R.string.reason_malformed_manifest
        ModelRejectionReason.IO_ERROR -> R.string.reason_io_error
        ModelRejectionReason.UNKNOWN_VERSION -> R.string.reason_unknown_version
        ModelRejectionReason.NO_PREVIOUS_VERSION -> R.string.reason_no_previous_version
        ModelRejectionReason.SWAP_REFUSED -> R.string.reason_swap_refused
        ModelRejectionReason.LOAD_FAILED -> R.string.reason_load_failed
    }

@Composable
private fun ModelCard(
    row: ModelRow,
    enabled: Boolean,
    onActivate: () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth(), blur = false, elevated = false, fill = Tint) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TouchTarget).padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.model_row_version, row.version.value),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (row.active || row.previous) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (row.active) {
                            SelectionChip(
                                stringResource(R.string.badge_active),
                                DesignR.drawable.ic_check_circle,
                                active = true,
                            )
                        }
                        if (row.previous) {
                            SelectionChip(
                                stringResource(R.string.badge_previous),
                                DesignR.drawable.ic_history,
                                active = false,
                            )
                        }
                    }
                }
            }
            if (!row.active) {
                PrimaryButton(
                    onActivate,
                    Modifier.heightIn(min = TouchTarget),
                    enabled = enabled,
                    contentPadding = 24.dp,
                ) {
                    Text(stringResource(R.string.action_activate), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Preview
@Composable
private fun ModelsContentPreview() {
    ModelsContent(
        ui =
            ModelsUiState(
                rows =
                    listOf(
                        ModelRow(ModelVersion("2.0.0"), active = true, previous = false),
                        ModelRow(ModelVersion("1.0.0"), active = false, previous = true),
                    ),
                outcome = ModelsOutcome.Imported(ModelVersion("2.0.0")),
            ),
        canSwap = true,
        onBack = {},
        onActivate = {},
        onRollback = {},
        onImport = {},
    )
}
