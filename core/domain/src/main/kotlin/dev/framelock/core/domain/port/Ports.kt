package dev.framelock.core.domain.port

import dev.framelock.core.domain.frame.DisconnectReason
import dev.framelock.core.domain.frame.Frame
import dev.framelock.core.domain.frame.SessionId
import dev.framelock.core.domain.frame.StreamSpec
import dev.framelock.core.domain.inference.InferenceResult
import dev.framelock.core.domain.model.AuditEntry
import dev.framelock.core.domain.model.ModelArtifact
import dev.framelock.core.domain.model.ModelManifest
import dev.framelock.core.domain.model.ModelSource
import dev.framelock.core.domain.model.ModelVersion
import dev.framelock.core.domain.system.DeviceSnapshot
import dev.framelock.core.domain.system.MemoryPressure
import dev.framelock.core.domain.system.ThermalLevel
import dev.framelock.core.domain.telemetry.TelemetryEvent
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
    /** A new session started; the source allocated [sessionId], strictly greater than any previous one. */
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

interface ModelRepository {
    fun active(): ModelArtifact?

    fun previous(): ModelArtifact?

    fun artifact(version: ModelVersion): ModelArtifact?

    fun list(): List<ModelManifest>

    suspend fun import(source: ModelSource): Result<ModelManifest>

    suspend fun activate(version: ModelVersion): Result<Unit>

    suspend fun rollback(): Result<Unit>
}

interface AuditLog {
    fun record(entry: AuditEntry)
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

interface TelemetrySink {
    fun record(event: TelemetryEvent)
}

/** Monotonic time on the realtime base (`elapsedRealtimeNanos` on Android, ADR-011). */
interface Clock {
    fun nowNanos(): Long
}

enum class TraceSection { CAPTURE, PREPROCESS, INFERENCE, POSTPROCESS, RENDER }

/** ADR-009: begin and end on the same thread; use [trace] rather than calling them directly. */
interface Tracer {
    fun begin(section: TraceSection)

    fun end()
}

inline fun <T> Tracer.trace(
    section: TraceSection,
    block: () -> T,
): T {
    begin(section)
    try {
        return block()
    } finally {
        end()
    }
}
