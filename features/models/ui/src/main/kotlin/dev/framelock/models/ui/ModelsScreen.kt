package dev.framelock.models.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.models.domain.ModelRejectionReason

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
    CompositionLocalProvider(LocalGlassHaze provides haze) {
        Box(modifier.fillMaxSize().background(BackdropBrush)) {
            // The list is what the top bar and the dock blur as it scrolls under them.
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .hazeSource(
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
                if (ui.rows.isEmpty()) item { EmptyState() }
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
    GlassSurface(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            .padding(Margin),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(onBack, Modifier.size(TouchTarget), contentPadding = 0.dp) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    stringResource(R.string.action_back),
                    Modifier.size(28.dp),
                )
            }
            Text(
                stringResource(R.string.models_title),
                Modifier.padding(start = 16.dp),
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
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
    GlassSurface(Modifier.fillMaxWidth(), blur = false, elevated = false, fill = Tint) {
        Text(
            text,
            Modifier
                .fillMaxWidth()
                .background(if (refused) DangerFill else Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            color = TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun EmptyState() {
    GlassSurface(Modifier.fillMaxWidth(), blur = false, elevated = false, fill = Tint) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_upload_file),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(48.dp)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(AccentBrush, blendMode = BlendMode.SrcIn)
                        },
                tint = Color.White,
            )
            Text(
                stringResource(R.string.models_empty),
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.models_empty_hint),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
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
    GlassSurface(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(Margin),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(
                onClick = onRollback,
                modifier = Modifier.weight(1f).heightIn(min = TouchTarget),
                enabled = enabled && canRollBack,
            ) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = null, modifier = Modifier.size(24.dp))
                Text(
                    stringResource(R.string.action_rollback),
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
            GlassButton(
                onClick = onImport,
                modifier = Modifier.weight(1f).heightIn(min = TouchTarget),
                enabled = enabled,
                look = ButtonLook.Accent,
            ) {
                Icon(
                    painterResource(R.drawable.ic_upload_file),
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
    GlassSurface(Modifier.fillMaxWidth(), blur = false, elevated = false, fill = Tint) {
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
                            StatusChip(stringResource(R.string.badge_active), R.drawable.ic_check_circle, active = true)
                        }
                        if (row.previous) {
                            StatusChip(stringResource(R.string.badge_previous), R.drawable.ic_history, active = false)
                        }
                    }
                }
            }
            if (!row.active) {
                GlassButton(
                    onActivate,
                    Modifier.heightIn(min = TouchTarget),
                    enabled = enabled,
                    look = ButtonLook.Accent,
                    contentPadding = 24.dp,
                ) {
                    Text(stringResource(R.string.action_activate), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** "Active" has the gradient for its edge, "Previous" the plain glass one. */
@Composable
private fun StatusChip(
    text: String,
    icon: Int,
    active: Boolean,
) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Tint)
            .border(if (active) 1.5.dp else 1.dp, if (active) AccentBrush else SolidColor(Edge), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (active) AccentCyan else TextSecondary,
        )
        Text(text, color = TextPrimary, style = MaterialTheme.typography.labelLarge)
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
