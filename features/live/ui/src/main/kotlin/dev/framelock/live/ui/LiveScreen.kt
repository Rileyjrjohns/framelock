package dev.framelock.live.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.live.application.metrics.LiveMetrics
import dev.framelock.live.application.pipeline.AnnotatedFrame
import dev.framelock.live.application.pipeline.PipelineUiState
import dev.framelock.live.application.usecase.StartResult
import dev.framelock.live.domain.inference.LabelId
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import java.util.Locale

/** Minimum touch target: usable with gloves and at a glance (docs/architecture.md, "UI"). */
private val TouchTarget = 64.dp

/**
 * The live screen: the analysed frame with its overlay, drawn together (ADR-001), a large status banner, the
 * start/stop control and the debug HUD.
 */
@Composable
fun LiveScreen(
    viewModel: LiveViewModel,
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
    Column(modifier.fillMaxSize().background(Color.Black)) {
        StatusBanner(ui, startResult)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ui.annotated?.let { annotated ->
                AnalysedFrame(annotated, viewModel.renderer::bitmapFor, viewModel.labelOf, viewModel::onDrawn)
            }
            if (hudVisible) Hud(live, description, thermal, memory, Modifier.align(Alignment.TopStart))
        }
        Controls(
            running = ui.state != PipelineState.Idle,
            selected = selected,
            options = options,
            onOpenSources = viewModel::refreshSources,
            onSelectSource = viewModel::selectSource,
            onStart = viewModel::start,
            onStop = viewModel::stop,
            onToggleHud = viewModel::toggleHud,
            onTrack = viewModel.track,
        )
    }
}

@Composable
private fun StatusBanner(
    ui: PipelineUiState,
    startResult: StartResult?,
) {
    val (text, color) = bannerOf(ui.state)
    Column(Modifier.fillMaxWidth().background(color).padding(16.dp)) {
        Text(text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        ui.model?.let { Text(stringResource(R.string.model_version, it.value), color = Color.White, fontSize = 18.sp) }
        when (startResult) {
            StartResult.NoActiveModel -> {
                Text(stringResource(R.string.start_no_model), color = Color.White)
            }

            is StartResult.LoadFailed -> {
                val reason = startResult.cause.message ?: startResult.cause::class.simpleName.orEmpty()
                Text(stringResource(R.string.start_load_failed, reason), color = Color.White)
            }

            else -> {
                Unit
            }
        }
    }
}

/** Colours chosen for WCAG AA contrast with white text. */
@Composable
private fun bannerOf(state: PipelineState): Pair<String, Color> =
    when (state) {
        PipelineState.Idle -> stringResource(R.string.state_idle) to Color(0xFF424242)
        PipelineState.Connecting -> stringResource(R.string.state_connecting) to Color(0xFF1A4F8B)
        is PipelineState.Running -> stringResource(R.string.state_running) to Color(0xFF1B5E20)
        is PipelineState.Degraded -> stringResource(R.string.state_degraded, state.cause.label()) to Color(0xFF8A4B00)
        is PipelineState.Paused -> stringResource(R.string.state_paused, state.cause.label()) to Color(0xFFB71C1C)
        is PipelineState.NoSignal -> stringResource(R.string.state_no_signal) to Color(0xFFB71C1C)
    }

private fun Enum<*>.label(): String = name.lowercase(Locale.ROOT).replace('_', ' ')

/** Draws the analysed frame and, on the same pixels, its detections; then reports the render (ADR-011). */
@Composable
private fun AnalysedFrame(
    annotated: AnnotatedFrame,
    bitmapFor: (AnnotatedFrame) -> Bitmap?,
    labelOf: (LabelId) -> String,
    onDraw: (AnnotatedFrame) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val bitmap = bitmapFor(annotated) ?: return@Canvas
            val fit = fitInto(size, annotated.width.toFloat(), annotated.height.toFloat())
            drawImage(
                bitmap.asImageBitmap(),
                dstOffset = IntOffset(fit.left.toInt(), fit.top.toInt()),
                dstSize = IntSize(fit.width.toInt(), fit.height.toInt()),
            )
            annotated.result.detections.forEach { detection ->
                val box = detection.box
                drawRect(
                    color = OverlayColor,
                    topLeft = Offset(fit.left + box.left * fit.width, fit.top + box.top * fit.height),
                    size = Size(box.width * fit.width, box.height * fit.height),
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
            onDraw(annotated)
        }
        // Labels as text, laid out over the canvas: legible at a glance.
        Column(Modifier.padding(8.dp)) {
            annotated.result.detections.take(MAX_LABELS).forEach { detection ->
                Text(
                    "${labelOf(detection.label)} ${(detection.score * PERCENT).toInt()}%",
                    color = OverlayColor,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

private val OverlayColor = Color(0xFF00E5FF)
private const val MAX_LABELS = 5
private const val PERCENT = 100

private data class Fit(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/** Letterboxes a [frameWidth] x [frameHeight] image into [area], keeping its aspect ratio. */
private fun DrawScope.fitInto(
    area: Size,
    frameWidth: Float,
    frameHeight: Float,
): Fit {
    val scale = minOf(area.width / frameWidth, area.height / frameHeight)
    val width = frameWidth * scale
    val height = frameHeight * scale
    return Fit((area.width - width) / 2, (area.height - height) / 2, width, height)
}

@Composable
private fun Hud(
    live: LiveMetrics,
    sourceDescription: String?,
    thermal: ThermalLevel,
    memory: MemoryPressure,
    modifier: Modifier = Modifier,
) {
    val ms = { value: Double? -> value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "–" }
    val metrics =
        stringResource(
            R.string.hud_line,
            live.inputFps,
            live.inferenceFps,
            ms(live.inferenceMs),
            ms(live.e2eMs),
            live.droppedFrames,
        )
    Column(modifier.background(Color(0xCC000000)).padding(8.dp)) {
        // The source line changes only when a session starts.
        sourceDescription?.let { Text(stringResource(R.string.hud_source, it), color = Color.White, fontSize = 14.sp) }
        Text(metrics, color = Color.White, fontSize = 14.sp)
        Text(stringResource(R.string.hud_system, thermal.name, memory.name), color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun Controls(
    running: Boolean,
    selected: SourceOption,
    options: List<SourceOption>,
    onOpenSources: () -> Unit,
    onSelectSource: (SourceOption) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleHud: () -> Unit,
    onTrack: (ElementId) -> Unit,
) {
    // Two rows: three buttons side by side do not fit a phone in portrait.
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SourcePicker(
                !running,
                selected,
                options,
                onOpenSources,
                onSelectSource,
                onTrack,
                Modifier.weight(1f),
            )
            OutlinedButton(onToggleHud, Modifier.heightIn(min = TouchTarget).track(ElementId.TOGGLE_HUD, onTrack)) {
                Text(stringResource(R.string.action_hud), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        if (running) {
            Button(onStop, Modifier.fillMaxWidth().heightIn(min = TouchTarget).track(ElementId.STOP, onTrack)) {
                Text(stringResource(R.string.action_stop), fontSize = 22.sp)
            }
        } else {
            Button(onStart, Modifier.fillMaxWidth().heightIn(min = TouchTarget).track(ElementId.START, onTrack)) {
                Text(stringResource(R.string.action_start), fontSize = 22.sp)
            }
        }
    }
}

/**
 * The source button and its menu. Enabled only while the pipeline is stopped: the source is chosen when a session
 * opens, so a change made while running would only apply at the next start. The options are listed afresh each time
 * the menu opens.
 */
@Composable
private fun SourcePicker(
    enabled: Boolean,
    selected: SourceOption,
    options: List<SourceOption>,
    onOpen: () -> Unit,
    onSelect: (SourceOption) -> Unit,
    onTrack: (ElementId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = {
                onOpen()
                expanded = true
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget).track(ElementId.SELECT_SOURCE, onTrack),
            enabled = enabled,
        ) {
            Text(stringResource(R.string.action_source, selected.label), color = MaterialTheme.colorScheme.onPrimary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, fontSize = 18.sp) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier.heightIn(min = TouchTarget).track(ElementId.CHOOSE_SOURCE, onTrack),
                )
            }
        }
    }
}
