package dev.framelock.live.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.application.pipeline.AnnotatedFrame
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.application.usecase.StartResult
import dev.framelock.live.domain.inference.LabelId
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel

/** Widest the status and message column grows on a wide (landscape) screen. */
private val StatusMaxWidth = 440.dp

/**
 * The live screen: the analysed frame with its overlay, drawn together (ADR-001), full-bleed under translucent
 * status, debug HUD and controls. In portrait the controls form a bar over the bottom of the video; in landscape they
 * form a rail beside it, so the rail never hides part of the analysed frame.
 */
@Composable
fun LiveScreen(
    viewModel: LiveViewModel,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val hudVisible by viewModel.hudVisible.collectAsStateWithLifecycle()
    val startResult by viewModel.startResult.collectAsStateWithLifecycle()
    val selected by viewModel.selectedSource.collectAsStateWithLifecycle()
    val options by viewModel.sourceOptions.collectAsStateWithLifecycle()
    val description by viewModel.sourceDescription.collectAsStateWithLifecycle()
    val thermal by viewModel.thermal.collectAsStateWithLifecycle()
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    val controls =
        ControlsState(
            running = ui.state != PipelineState.Idle,
            hudVisible = hudVisible,
            selected = selected,
            options = options,
        )
    val actions =
        ControlsActions(
            onOpenSources = viewModel::refreshSources,
            onSelectSource = viewModel::selectSource,
            onStart = viewModel::start,
            onStop = viewModel::stop,
            onToggleHud = viewModel::toggleHud,
            onOpenModels = onOpenModels,
            onTrack = viewModel.track,
        )
    // The matte around the frame is black whatever the theme: it is part of the video, not of the interface.
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        val video =
            @Composable { videoModifier: Modifier ->
                VideoArea(
                    ui = ui,
                    startResult = startResult,
                    hud = if (hudVisible) HudState(live, description, thermal, memory) else null,
                    landscape = landscape,
                    bitmapFor = viewModel.renderer::bitmapFor,
                    labelOf = viewModel.labelOf,
                    onDraw = viewModel::onDrawn,
                    modifier = videoModifier,
                )
            }
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                video(Modifier.weight(1f))
                ControlsRail(controls, actions)
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                video(Modifier.fillMaxSize())
                ControlsBar(controls, actions, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** What the debug HUD shows; null while it is hidden. */
private class HudState(
    val live: LiveMetrics,
    val sourceDescription: String?,
    val thermal: ThermalLevel,
    val memory: MemoryPressure,
)

/** The analysed frame with the status, start messages and HUD floating over it. */
@Composable
private fun VideoArea(
    ui: PipelineUiState,
    startResult: StartResult?,
    hud: HudState?,
    landscape: Boolean,
    bitmapFor: (AnnotatedFrame) -> Bitmap?,
    labelOf: (LabelId) -> String,
    onDraw: (AnnotatedFrame) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        val annotated = ui.annotated
        if (annotated != null) {
            AnalysedFrame(annotated, bitmapFor, labelOf, onDraw)
        } else if (ui.state == PipelineState.Idle) {
            IdleHint(Modifier.align(Alignment.Center))
        }
        // The rail (landscape) or the bar (portrait) takes the insets on its own side.
        val sides =
            if (landscape) {
                WindowInsetsSides.Start + WindowInsetsSides.Top
            } else {
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            }
        val overlay = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(sides)).padding(12.dp)
        if (landscape) {
            // The status takes the width left by the HUD, up to its maximum; a spacer sharing the weight would squeeze
            // it into a narrow column.
            Row(overlay, horizontalArrangement = Arrangement.SpaceBetween) {
                StatusOverlay(ui, startResult, Modifier.weight(1f, fill = false).widthIn(max = StatusMaxWidth))
                hud?.let { Hud(it.live, it.sourceDescription, it.thermal, it.memory) }
            }
        } else {
            Column(overlay, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusOverlay(ui, startResult, Modifier.fillMaxWidth())
                hud?.let { Hud(it.live, it.sourceDescription, it.thermal, it.memory, Modifier.align(Alignment.End)) }
            }
        }
    }
}

/** Shown while stopped with nothing to draw, so the screen is never an empty black void. */
@Composable
internal fun IdleHint(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(PaddingValues(horizontal = 32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_videocam),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.idle_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}
