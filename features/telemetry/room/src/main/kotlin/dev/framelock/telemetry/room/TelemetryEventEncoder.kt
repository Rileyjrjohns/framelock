package dev.framelock.telemetry.room

import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.kernel.telemetry.UsageTelemetry
import dev.framelock.live.domain.telemetry.LiveTelemetry
import dev.framelock.models.domain.telemetry.ModelTelemetry
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Turns an accepted event into one JSON object: `{"type":"<EventName>","atMillis":<wall clock>, ...fields}`. Fields are
 * numbers, booleans, enum names and the value of a value class; a missing latency is `null` (ADR-004, M6). Written by
 * hand, not with annotations: the event classes live in pure modules, which cannot use the serialization plugin.
 *
 * Every family's `when` has no `else`: a new event must be given its encoding here before the code compiles.
 */
object TelemetryEventEncoder {
    /** The JSON text of [event] recorded at [atMillis] (wall clock), or null for a family this encoder does not know. */
    fun encode(
        event: TelemetryEvent,
        atMillis: Long,
    ): String? {
        val body: JsonObjectBuilder.() -> Unit =
            when (event) {
                is UsageTelemetry -> usage(event)
                is LiveTelemetry -> live(event)
                is ModelTelemetry -> model(event)
                else -> return null
            }
        return buildJsonObject {
            put("type", typeName(event))
            put("atMillis", atMillis)
            body()
        }.toString()
    }

    private fun typeName(event: TelemetryEvent): String =
        when (event) {
            is UsageTelemetry -> usageName(event)
            is LiveTelemetry -> liveName(event)
            is ModelTelemetry -> modelName(event)
            else -> error("Unknown event ${event::class}")
        }

    private fun usageName(event: UsageTelemetry): String =
        when (event) {
            UsageTelemetry.AppSession -> "AppSession"
            is UsageTelemetry.ScreenViewed -> "ScreenViewed"
            is UsageTelemetry.UiInteraction -> "UiInteraction"
        }

    private fun liveName(event: LiveTelemetry): String =
        when (event) {
            is LiveTelemetry.PipelineWindow -> "PipelineWindow"
            is LiveTelemetry.DeviceSnapshotRecorded -> "DeviceSnapshotRecorded"
            is LiveTelemetry.PipelineDegraded -> "PipelineDegraded"
        }

    private fun modelName(event: ModelTelemetry): String =
        when (event) {
            is ModelTelemetry.ModelSwapped -> "ModelSwapped"
            is ModelTelemetry.ModelImported -> "ModelImported"
            is ModelTelemetry.ModelRejected -> "ModelRejected"
        }

    private fun usage(event: UsageTelemetry): JsonObjectBuilder.() -> Unit =
        when (event) {
            UsageTelemetry.AppSession -> {
                {}
            }

            is UsageTelemetry.ScreenViewed -> {
                { put("screen", event.screen.name) }
            }

            is UsageTelemetry.UiInteraction -> {
                { put("element", event.element.name) }
            }
        }

    private fun live(event: LiveTelemetry): JsonObjectBuilder.() -> Unit =
        when (event) {
            is LiveTelemetry.PipelineWindow -> {
                {
                    put("inferenceMsP50", event.inferenceMsP50)
                    put("inferenceMsP95", event.inferenceMsP95)
                    put("e2eMsP50", event.e2eMsP50)
                    put("e2eMsP95", event.e2eMsP95)
                    put("inputFps", event.inputFps)
                    put("inferenceFps", event.inferenceFps)
                    put("droppedFrames", event.droppedFrames)
                }
            }

            is LiveTelemetry.DeviceSnapshotRecorded -> {
                {
                    put("model", event.model)
                    put("osVersion", event.osVersion)
                    put("batteryPct", event.batteryPct)
                    put("batteryTempC", event.batteryTempC)
                    put("thermalLevel", event.thermalLevel.name)
                }
            }

            is LiveTelemetry.PipelineDegraded -> {
                { put("cause", event.cause.name) }
            }
        }

    private fun model(event: ModelTelemetry): JsonObjectBuilder.() -> Unit =
        when (event) {
            is ModelTelemetry.ModelSwapped -> {
                {
                    put("from", event.from?.value)
                    put("to", event.to.value)
                }
            }

            is ModelTelemetry.ModelImported -> {
                { put("version", event.version.value) }
            }

            is ModelTelemetry.ModelRejected -> {
                { put("reason", event.reason.name) }
            }
        }
}
