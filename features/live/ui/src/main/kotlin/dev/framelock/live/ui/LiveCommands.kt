package dev.framelock.live.ui

import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.live.application.usecase.StartResult

interface LiveCommands {
    suspend fun start(): StartResult

    suspend fun stop()

    suspend fun sources(): List<SourceOption>

    fun select(id: String)

    fun track(element: ElementId)
}
