package dev.framelock.live.domain.port

import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.frame.StreamSpec
import dev.framelock.live.domain.inference.InferenceResult
import dev.framelock.live.domain.system.DeviceSnapshot
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * A video source. Collect [events] before calling [open]. Frames that are never delivered are closed by the source
 * (ADR-010); every delivered frame belongs to the collector.
 */
interface FrameSource {
    val events: Flow<SourceEvent>

    suspend fun open()

    suspend fun close()
}

sealed interface SourceEvent {
    /**
     * A new session started. The source allocated [sessionId], strictly greater than any id it emitted before, for
     * the whole lifetime of the source, across close() and open(): the pipeline refuses a smaller or equal one.
     */
    data class Connected(
        val spec: StreamSpec,
        val sessionId: SessionId,
    ) : SourceEvent

    data class FrameAvailable(
        val frame: Frame,
    ) : SourceEvent

    data class Disconnected(
        val reason: DisconnectReason,
    ) : SourceEvent
}

/** ADR-002: [load], [infer] and [close] run on [dispatcher], a single thread created by the adapter. */
interface InferenceEngine : AutoCloseable {
    val dispatcher: CoroutineDispatcher

    /** Loads and warms up [model]; the previous model stays loaded if this throws. */
    suspend fun load(model: ModelArtifact)

    /** Blocking. Does not close the frame's buffer: the caller owns it. */
    fun infer(frame: Frame): InferenceResult
}

interface ThermalMonitor {
    val level: Flow<ThermalLevel>
}

interface MemoryPressureMonitor {
    val pressure: Flow<MemoryPressure>
}

interface DeviceInfoProvider {
    fun snapshot(): DeviceSnapshot
}
