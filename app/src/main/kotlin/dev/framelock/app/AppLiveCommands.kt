package dev.framelock.app

import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.live.application.usecase.StartPipeline
import dev.framelock.live.application.usecase.StopPipeline
import dev.framelock.live.ui.LiveCommands
import dev.framelock.telemetry.application.TrackUiInteraction

internal class AppLiveCommands(
    private val start: StartPipeline,
    private val stop: StopPipeline,
    private val sources: SourceWiring,
    private val track: TrackUiInteraction,
) : LiveCommands {
    override suspend fun start() = start.invoke()

    override suspend fun stop() = stop.invoke()

    override suspend fun sources() = sources.selection.options()

    override fun select(id: String) = sources.selection.select(id)

    override fun track(element: ElementId) = track.invoke(element)
}
