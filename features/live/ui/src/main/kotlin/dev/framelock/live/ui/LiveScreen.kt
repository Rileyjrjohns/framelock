package dev.framelock.live.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import dev.framelock.designsystem.EmptyState
import dev.framelock.designsystem.GlassPalette
import dev.framelock.designsystem.IdleBrush
import dev.framelock.designsystem.LocalGlassHaze
import dev.framelock.designsystem.glassSource
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.application.pipeline.AnnotatedFrame
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.application.usecase.StartResult
import dev.framelock.live.domain.inference.LabelId
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import dev.framelock.designsystem.R as DesignR

private val IdleCardMaxWidth = 340.dp
private val IdleIconSize = 48.dp

/** Room the top status bar takes over the video, so the idle card is centred below it. */
private val TopBarAllowance = 88.dp

/**
 * The live screen: the analysed frame with its overlay, drawn together (ADR-001), full-bleed under panels of frosted
 * glass: status, debug HUD and controls. The video area is the blur source (`hazeSource`); the panels blur what is
 * behind them. In portrait the controls form a dock over the bottom of the video; in landscape they form a rail beside
 * it, so the rail never hides part of the analysed frame.
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
    val backend by viewModel.backend.collectAsStateWithLifecycle()
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
    val haze = rememberHazeState()
    CompositionLocalProvider(LocalGlassHaze provides haze) {
        LiveLayout(
            ui = ui,
            live = live,
            startResult = startResult,
            // Stopped, the metrics are those of the last session: the HUD only shows while something runs.
            hud =
                if (hudVisible &&
                    ui.state != PipelineState.Idle
                ) {
                    HudState(description, backend, thermal, memory)
                } else {
                    null
                },
            controls = controls,
            actions = actions,
            haze = haze,
            frames = FrameCallbacks(viewModel.renderer::bitmapFor, viewModel.labelOf, viewModel::onDrawn),
            modifier = modifier,
        )
    }
}

@Composable
private fun LiveLayout(
    ui: PipelineUiState,
    live: LiveMetrics,
    startResult: StartResult?,
    hud: HudState?,
    controls: ControlsState,
    actions: ControlsActions,
    haze: HazeState,
    frames: FrameCallbacks,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color(GlassPalette.BACKGROUND))) {
        val landscape = maxWidth > maxHeight
        val video =
            @Composable { videoModifier: Modifier ->
                VideoArea(
                    ui = ui,
                    live = live,
                    startResult = startResult,
                    hud = hud,
                    haze = haze,
                    landscape = landscape,
                    frames = frames,
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

/** What the analysed frame needs from the view model: its pixels, the label names, and the report that it was drawn. */
private class FrameCallbacks(
    val bitmapFor: (AnnotatedFrame, Int, Int) -> Bitmap?,
    val labelOf: (LabelId) -> String,
    val onDraw: (AnnotatedFrame) -> Unit,
)

/** What the debug HUD shows; null while it is hidden. */
private class HudState(
    val sourceDescription: String?,
    val backend: String?,
    val thermal: ThermalLevel,
    val memory: MemoryPressure,
)

/** The analysed frame with the status, start messages and HUD floating over it. */
@Composable
private fun VideoArea(
    ui: PipelineUiState,
    live: LiveMetrics,
    startResult: StartResult?,
    hud: HudState?,
    haze: HazeState,
    landscape: Boolean,
    frames: FrameCallbacks,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        val annotated = ui.annotated
        val idle = annotated == null && ui.state == PipelineState.Idle
        // The matte around the frame is black whatever the theme: it is part of the video, not of the interface. This
        // layer is what the glass panels blur, so the panels themselves are siblings of it, never inside it.
        Box(
            Modifier
                .fillMaxSize()
                .glassSource(haze)
                .background(if (idle) IdleBrush else SolidColor(Color.Black)),
        ) {
            if (annotated != null) AnalysedFrame(annotated, frames.bitmapFor, frames.labelOf, frames.onDraw)
        }
        // Centred in the space under the top bar; on a short (landscape) screen the card drops its icon to fit.
        if (idle) IdleHint(Modifier.align(Alignment.Center).padding(top = TopBarAllowance), compact = landscape)
        // The rail (landscape) or the dock (portrait) takes the insets on its own side.
        val sides =
            if (landscape) {
                WindowInsetsSides.Start + WindowInsetsSides.Top
            } else {
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            }
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(sides)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusOverlay(ui, startResult, live, showReadout = hud == null, Modifier.fillMaxWidth())
            hud?.let {
                Hud(
                    live,
                    it.sourceDescription,
                    it.backend,
                    it.thermal,
                    it.memory,
                    Modifier.align(Alignment.End),
                )
            }
        }
    }
}

/** Shown while stopped with nothing to draw, so the screen is never an empty void: a glass card over the gradient. */
@Composable
internal fun IdleHint(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    EmptyState(
        stringResource(R.string.idle_title),
        stringResource(R.string.idle_hint),
        DesignR.drawable.ic_videocam,
        modifier,
        compact,
    )
}

@Preview(widthDp = 400, heightDp = 400)
@Composable
private fun IdleHintPreview() {
    Box(Modifier.fillMaxSize().background(IdleBrush), contentAlignment = Alignment.Center) { IdleHint() }
}
