package dev.framelock.telemetry.domain

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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TelemetryAllowlistTest {
    private val accepted = AllowlistVerdict.Accepted

    private val validWindow =
        LiveTelemetry.PipelineWindow(
            inferenceMsP50 = 10.0,
            inferenceMsP95 = 20.0,
            e2eMsP50 = 30.0,
            e2eMsP95 = 60.0,
            inputFps = 30.0,
            inferenceFps = 25.0,
            droppedFrames = 3,
        )

    private val validSnapshot =
        LiveTelemetry.DeviceSnapshotRecorded(
            model = "Device 8",
            osVersion = "14",
            batteryPct = 80,
            batteryTempC = 31.5f,
            thermalLevel = ThermalLevel.LIGHT,
        )

    @Test
    fun `event of an unknown family is rejected`() {
        val unknown = object : TelemetryEvent {}

        assertEquals(AllowlistVerdict.Rejected(AllowlistRejection.UNKNOWN_EVENT), TelemetryAllowlist.verdict(unknown))
    }

    @Test
    fun `every TelemetryEvent subtype is allowlisted`() {
        val oneOfEach: List<TelemetryEvent> =
            listOf(
                UsageTelemetry.AppSession,
                UsageTelemetry.ScreenViewed(ScreenId.LIVE),
                UsageTelemetry.UiInteraction(ElementId.START),
                validWindow,
                validSnapshot,
                ModelTelemetry.ModelSwapped(from = null, to = ModelVersion("v2")),
                ModelTelemetry.ModelRejected(ModelRejectionReason.BAD_SIGNATURE),
                LiveTelemetry.PipelineDegraded(DegradationCause.THERMAL),
            )

        val names =
            oneOfEach.map { event ->
                // No `else` inside a family: a new event stops this test from compiling until it is covered here.
                when (event) {
                    is UsageTelemetry -> {
                        when (event) {
                            UsageTelemetry.AppSession -> "AppSession"
                            is UsageTelemetry.ScreenViewed -> "ScreenViewed"
                            is UsageTelemetry.UiInteraction -> "UiInteraction"
                        }
                    }

                    is LiveTelemetry -> {
                        when (event) {
                            is LiveTelemetry.PipelineWindow -> "PipelineWindow"
                            is LiveTelemetry.DeviceSnapshotRecorded -> "DeviceSnapshotRecorded"
                            is LiveTelemetry.PipelineDegraded -> "PipelineDegraded"
                        }
                    }

                    is ModelTelemetry -> {
                        when (event) {
                            is ModelTelemetry.ModelSwapped -> "ModelSwapped"
                            is ModelTelemetry.ModelRejected -> "ModelRejected"
                        }
                    }

                    else -> {
                        error("unknown family: $event")
                    }
                }
            }

        assertEquals(oneOfEach.size, names.toSet().size, "each subtype is listed once")
        oneOfEach.forEach { assertEquals(accepted, TelemetryAllowlist.verdict(it), "$it") }
    }

    @Test
    fun `valid events of every type are accepted, including AppSession`() {
        val noSamples =
            validWindow.copy(
                inferenceMsP50 = null,
                inferenceMsP95 = null,
                e2eMsP50 = null,
                e2eMsP95 = null,
                inputFps = 0.0,
                inferenceFps = 0.0,
                droppedFrames = 0,
            )
        val valid =
            listOf(
                UsageTelemetry.AppSession,
                UsageTelemetry.ScreenViewed(ScreenId.MODELS),
                UsageTelemetry.UiInteraction(ElementId.ROLLBACK_MODEL),
                // A window without latency samples, and the lowest values.
                noSamples,
                // Equal percentiles, and one percentile alone.
                validWindow.copy(inferenceMsP50 = 10.0, inferenceMsP95 = 10.0, e2eMsP50 = 5.0, e2eMsP95 = null),
                validSnapshot.copy(batteryPct = 0),
                validSnapshot.copy(batteryPct = 100, batteryTempC = -5.5f),
                validSnapshot.copy(model = "a".repeat(64), osVersion = "OS 14.0_beta-1"),
                ModelTelemetry.ModelSwapped(from = ModelVersion("v1"), to = ModelVersion("v2")),
                ModelTelemetry.ModelRejected(ModelRejectionReason.LOAD_FAILED),
                LiveTelemetry.PipelineDegraded(DegradationCause.ENGINE_ERROR),
            )

        valid.forEach { assertEquals(accepted, TelemetryAllowlist.verdict(it), "$it") }
    }

    @ParameterizedTest
    @ValueSource(doubles = [Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY])
    fun `PipelineWindow with NaN or infinite metric is rejected`(value: Double) {
        val windows =
            mapOf(
                "inferenceMsP50" to validWindow.copy(inferenceMsP50 = value),
                "inferenceMsP95" to validWindow.copy(inferenceMsP95 = value),
                "e2eMsP50" to validWindow.copy(e2eMsP50 = value),
                "e2eMsP95" to validWindow.copy(e2eMsP95 = value),
                "inputFps" to validWindow.copy(inputFps = value),
                "inferenceFps" to validWindow.copy(inferenceFps = value),
            )

        // Also for -Infinity, which is negative too: the non-finite reason comes first.
        windows.forEach { (field, event) ->
            assertEquals(rejected(AllowlistRejection.NON_FINITE_METRIC), TelemetryAllowlist.verdict(event), field)
        }
    }

    @Test
    fun `negative counts or fps are rejected`() {
        val windows =
            mapOf(
                "droppedFrames" to validWindow.copy(droppedFrames = -1),
                "inputFps" to validWindow.copy(inputFps = -0.1),
                "inferenceFps" to validWindow.copy(inferenceFps = -1.0),
                "inferenceMsP50" to validWindow.copy(inferenceMsP50 = -1.0),
                "inferenceMsP95" to validWindow.copy(inferenceMsP50 = null, inferenceMsP95 = -1.0),
                "e2eMsP50" to validWindow.copy(e2eMsP50 = -1.0),
                "e2eMsP95" to validWindow.copy(e2eMsP50 = null, e2eMsP95 = -1.0),
            )

        windows.forEach { (field, event) ->
            assertEquals(rejected(AllowlistRejection.NEGATIVE_VALUE), TelemetryAllowlist.verdict(event), field)
        }
    }

    @Test
    fun `p95 lower than p50 is rejected`() {
        val inverted =
            mapOf(
                "inference" to validWindow.copy(inferenceMsP50 = 20.0, inferenceMsP95 = 19.9),
                "e2e" to validWindow.copy(e2eMsP50 = 50.0, e2eMsP95 = 10.0),
            )

        inverted.forEach { (name, event) ->
            assertEquals(rejected(AllowlistRejection.PERCENTILE_ORDER), TelemetryAllowlist.verdict(event), name)
        }
    }

    @Test
    fun `device model outside the bounded token charset or length is rejected`() {
        val unbounded =
            listOf(
                "",
                "a".repeat(65),
                "Device 8; DROP TABLE",
                "Device/8",
                "Device 8\n",
                "Device\t8",
                "user@example.com",
                "Devicé 8",
                "Device 8 (Pro)",
            )

        unbounded.forEach { text ->
            assertEquals(
                rejected(AllowlistRejection.UNBOUNDED_TEXT),
                TelemetryAllowlist.verdict(validSnapshot.copy(model = text)),
                "model '$text'",
            )
            assertEquals(
                rejected(AllowlistRejection.UNBOUNDED_TEXT),
                TelemetryAllowlist.verdict(validSnapshot.copy(osVersion = text)),
                "osVersion '$text'",
            )
        }
    }

    // The JVM does not allow ".." in a method name, so the readable name goes in the display name.
    @DisplayName("batteryPct outside 0..100 is rejected")
    @ParameterizedTest
    @ValueSource(ints = [-1, 101, Int.MIN_VALUE, Int.MAX_VALUE])
    fun `batteryPct outside 0 to 100 is rejected`(batteryPct: Int) {
        assertEquals(
            rejected(AllowlistRejection.OUT_OF_RANGE),
            TelemetryAllowlist.verdict(validSnapshot.copy(batteryPct = batteryPct)),
        )
    }

    @ParameterizedTest
    @ValueSource(floats = [Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY])
    fun `non-finite battery temperature is rejected`(batteryTempC: Float) {
        assertEquals(
            rejected(AllowlistRejection.NON_FINITE_METRIC),
            TelemetryAllowlist.verdict(validSnapshot.copy(batteryTempC = batteryTempC)),
        )
    }

    private fun rejected(reason: AllowlistRejection) = AllowlistVerdict.Rejected(reason)
}
