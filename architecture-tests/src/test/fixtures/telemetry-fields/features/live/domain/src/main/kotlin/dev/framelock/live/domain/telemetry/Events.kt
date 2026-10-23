package dev.framelock.live.domain.telemetry

import dev.framelock.kernel.telemetry.ScreenId
import dev.framelock.kernel.telemetry.TelemetryEvent

sealed interface LiveTelemetry : TelemetryEvent

@JvmInline
value class Count(val value: Int)

/** Allowed: primitives, a project enum, a project value class. */
data class GoodEvent(val fps: Double, val screen: ScreenId, val count: Count, val dropped: Int?) : LiveTelemetry

/** Not allowed: free text and raw bytes. */
data class LeakyEvent(val ok: Long, val note: String, val frame: ByteArray) : LiveTelemetry

/** The bounded strings listed in the rule. */
data class DeviceSnapshotRecorded(val model: String, val osVersion: String) : LiveTelemetry
