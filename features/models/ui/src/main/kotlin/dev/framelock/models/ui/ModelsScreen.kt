package dev.framelock.models.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.models.domain.ModelRejectionReason

/** Minimum touch target: usable with gloves and at a glance (docs/architecture.md, "UI"). */
private val TouchTarget = 64.dp

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

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.models_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onBack, Modifier.size(TouchTarget)) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = { Actions(enabled, ui.canRollBack, onRollback, onImport) },
    ) { padding ->
        val status = statusText(ui, canSwap)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            status?.let { (text, refused) -> item { StatusCard(text, refused) } }
            if (ui.rows.isEmpty()) item { EmptyState() }
            items(ui.rows, key = { it.version.value }) { row ->
                ModelCard(row, enabled, onActivate = { onActivate(row.version) })
            }
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
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (refused) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (refused) colors.onErrorContainer else colors.onSecondaryContainer,
    ) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_upload_file),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.models_empty), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.models_empty_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** Import and roll back, always in reach at the bottom. */
@Composable
private fun Actions(
    enabled: Boolean,
    canRollBack: Boolean,
    onRollback: () -> Unit,
    onImport: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = onRollback,
                modifier = Modifier.weight(1f).heightIn(min = TouchTarget),
                enabled = enabled && canRollBack,
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = null)
                Text(
                    stringResource(R.string.action_rollback),
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Button(
                onClick = onImport,
                modifier = Modifier.weight(1f).heightIn(min = TouchTarget),
                enabled = enabled,
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(painterResource(R.drawable.ic_upload_file), contentDescription = null)
                Text(
                    stringResource(R.string.action_import),
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
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
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = if (row.active) colors.surfaceContainerHigh else colors.surfaceContainer,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TouchTarget).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.model_row_version, row.version.value),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (row.active || row.previous) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (row.active) {
                            StatusChip(
                                stringResource(R.string.badge_active),
                                R.drawable.ic_check_circle,
                                colors.primaryContainer,
                                colors.onPrimaryContainer,
                            )
                        }
                        if (row.previous) {
                            StatusChip(
                                stringResource(R.string.badge_previous),
                                R.drawable.ic_history,
                                colors.secondaryContainer,
                                colors.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
            if (!row.active) {
                FilledTonalButton(
                    onActivate,
                    Modifier.heightIn(min = TouchTarget),
                    enabled = enabled,
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text(stringResource(R.string.action_activate), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    icon: Int,
    container: Color,
    content: Color,
) {
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
