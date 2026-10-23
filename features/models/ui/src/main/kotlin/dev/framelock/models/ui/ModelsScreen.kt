package dev.framelock.models.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val enabled = canSwap && !ui.busy
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onBack, Modifier.heightIn(min = TouchTarget)) {
                Text(stringResource(R.string.action_back), fontSize = 20.sp)
            }
            Text(
                stringResource(R.string.models_title),
                Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        StatusLine(ui, canSwap)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.rows.isEmpty()) {
                item { Text(stringResource(R.string.models_empty), color = MaterialTheme.colorScheme.onBackground) }
            }
            items(ui.rows, key = { it.version.value }) { row ->
                ModelRowItem(
                    row,
                    enabled,
                    onActivate = {
                        viewModel.track(ElementId.ACTIVATE_MODEL)
                        viewModel.activate(row.version)
                    },
                )
            }
        }
        Button(
            onClick = {
                viewModel.track(ElementId.ROLLBACK_MODEL)
                viewModel.rollback()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            enabled = enabled && ui.canRollBack,
        ) {
            Text(stringResource(R.string.action_rollback), fontSize = 22.sp)
        }
        Button(
            onClick = {
                viewModel.track(ElementId.IMPORT_MODEL)
                picker.launch(PickerMimeTypes)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            enabled = enabled,
        ) {
            Text(stringResource(R.string.action_import), fontSize = 22.sp)
        }
    }
}

/** Why the controls are off, or what the last action did. */
@Composable
private fun StatusLine(
    ui: ModelsUiState,
    canSwap: Boolean,
) {
    val text =
        when {
            ui.busy -> stringResource(R.string.models_busy)
            !canSwap -> stringResource(R.string.models_locked)
            else -> ui.outcome?.let { outcomeText(it) }
        }
    if (text != null) Text(text, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
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
private fun ModelRowItem(
    row: ModelRow,
    enabled: Boolean,
    onActivate: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.model_row_version, row.version.value),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
            )
            if (row.active) Badge(stringResource(R.string.badge_active))
            if (row.previous) Badge(stringResource(R.string.badge_previous))
        }
        if (!row.active) {
            OutlinedButton(onActivate, Modifier.heightIn(min = TouchTarget), enabled = enabled) {
                Text(stringResource(R.string.action_activate), fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}
