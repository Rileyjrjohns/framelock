package dev.framelock.telemetry.room

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.telemetry.ElementId
import dev.framelock.kernel.telemetry.ScreenId
import dev.framelock.kernel.telemetry.TelemetryEvent
import dev.framelock.kernel.telemetry.UsageTelemetry
import dev.framelock.live.domain.pipeline.DegradationCause
import dev.framelock.live.domain.system.ThermalLevel
import dev.framelock.live.domain.telemetry.LiveTelemetry
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.domain.telemetry.ModelTelemetry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TelemetryEventEncoderTest {
    private fun encode(event: TelemetryEvent): JsonObject =
        Json.parseToJsonElement(checkNotNull(TelemetryEventEncoder.encode(event, AT))).jsonObject

    private fun JsonObject.str(key: String) = (getValue(key) as JsonPrimitive).content

    private fun JsonObject.assertFields(vararg expected: Pair<String, Any?>) {
        assertEquals(expected.map { it.first }.toSet(), keys, "keys of $this")
        expected.forEach { (key, value) ->
            when (value) {
                null -> assertEquals(JsonNull, getValue(key), key)
                is String -> assertEquals(value, str(key), key)
                is Number -> assertEquals(value.toDouble(), str(key).toDouble(), key)
                else -> error("Unexpected expectation $value")
            }
        }
    }

    @Test
    fun `app session`() {
        encode(UsageTelemetry.AppSession).assertFields("type" to "AppSession", "atMillis" to AT)
    }

    @Test
    fun `screen viewed carries the screen name`() {
        encode(UsageTelemetry.ScreenViewed(ScreenId.MODELS))
            .assertFields("type" to "ScreenViewed", "atMillis" to AT, "screen" to "MODELS")
    }

    @Test
    fun `ui interaction carries the element name`() {
        encode(UsageTelemetry.UiInteraction(ElementId.TOGGLE_HUD))
            .assertFields("type" to "UiInteraction", "atMillis" to AT, "element" to "TOGGLE_HUD")
    }

    @Test
    fun `pipeline window carries every metric`() {
        val event = LiveTelemetry.PipelineWindow(10.5, 20.0, 30.25, 60.0, 29.9, 24.5, 3)

        encode(event).assertFields(
            "type" to "PipelineWindow",
            "atMillis" to AT,
            "inferenceMsP50" to 10.5,
            "inferenceMsP95" to 20.0,
            "e2eMsP50" to 30.25,
            "e2eMsP95" to 60.0,
            "inputFps" to 29.9,
            "inferenceFps" to 24.5,
            "droppedFrames" to 3,
        )
    }

    @Test
    fun `a window without samples encodes null latencies`() {
        val event = LiveTelemetry.PipelineWindow(null, null, null, null, 0.0, 0.0, 0)

        encode(event).assertFields(
            "type" to "PipelineWindow",
            "atMillis" to AT,
            "inferenceMsP50" to null,
            "inferenceMsP95" to null,
            "e2eMsP50" to null,
            "e2eMsP95" to null,
            "inputFps" to 0.0,
            "inferenceFps" to 0.0,
            "droppedFrames" to 0,
        )
    }

    @Test
    fun `device snapshot carries the bounded tokens and the thermal level name`() {
        val event = LiveTelemetry.DeviceSnapshotRecorded("Pixel 9", "17", 80, 36.5f, ThermalLevel.MODERATE)

        encode(event).assertFields(
            "type" to "DeviceSnapshotRecorded",
            "atMillis" to AT,
            "model" to "Pixel 9",
            "osVersion" to "17",
            "batteryPct" to 80,
            "batteryTempC" to 36.5,
            "thermalLevel" to "MODERATE",
        )
    }

    @Test
    fun `pipeline degraded carries the cause name`() {
        encode(LiveTelemetry.PipelineDegraded(DegradationCause.THERMAL_AND_MEMORY))
            .assertFields("type" to "PipelineDegraded", "atMillis" to AT, "cause" to "THERMAL_AND_MEMORY")
    }

    @Test
    fun `model swapped carries both versions as their value`() {
        encode(ModelTelemetry.ModelSwapped(ModelVersion("v1"), ModelVersion("v2.0-rc1")))
            .assertFields("type" to "ModelSwapped", "atMillis" to AT, "from" to "v1", "to" to "v2.0-rc1")
    }

    @Test
    fun `the first model swap has a null origin`() {
        encode(ModelTelemetry.ModelSwapped(null, ModelVersion("v1")))
            .assertFields("type" to "ModelSwapped", "atMillis" to AT, "from" to null, "to" to "v1")
    }

    @Test
    fun `model imported carries the version value`() {
        encode(ModelTelemetry.ModelImported(ModelVersion("v3")))
            .assertFields("type" to "ModelImported", "atMillis" to AT, "version" to "v3")
    }

    @Test
    fun `model rejected carries the reason name`() {
        encode(ModelTelemetry.ModelRejected(ModelRejectionReason.BAD_SIGNATURE))
            .assertFields("type" to "ModelRejected", "atMillis" to AT, "reason" to "BAD_SIGNATURE")
    }

    @Test
    fun `an event of an unknown family is not encoded`() {
        assertNull(TelemetryEventEncoder.encode(object : TelemetryEvent {}, AT))
    }

    @Test
    fun `an encoded event is one line`() {
        val text =
            checkNotNull(TelemetryEventEncoder.encode(LiveTelemetry.PipelineDegraded(DegradationCause.MEMORY), AT))

        assertEquals(
            """{"type":"PipelineDegraded","atMillis":$AT,"cause":"MEMORY"}""",
            text,
        )
    }

    private companion object {
        const val AT = 1_761_000_000_000L
    }
}
