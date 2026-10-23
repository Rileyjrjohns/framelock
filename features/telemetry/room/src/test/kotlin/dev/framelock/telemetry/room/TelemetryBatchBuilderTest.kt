package dev.framelock.telemetry.room

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TelemetryBatchBuilderTest {
    private val payloads =
        listOf(
            """{"type":"AppSession","atMillis":1}""",
            """{"type":"ScreenViewed","atMillis":2,"screen":"LIVE"}""",
            """{"type":"PipelineDegraded","atMillis":3,"cause":"THERMAL"}""",
        )

    @Test
    fun `the batch counts its events`() {
        assertEquals(3, TelemetryBatchBuilder.build(payloads).eventCount)
    }

    @Test
    fun `the gzipped bytes hold a valid JSON array of the events in order`() {
        val json = TelemetryBatchBuilder.json(TelemetryBatchBuilder.build(payloads))

        val array = Json.parseToJsonElement(json).jsonArray
        assertEquals(
            listOf("AppSession", "ScreenViewed", "PipelineDegraded"),
            array.map {
                it.jsonObject
                    .getValue("type")
                    .toString()
                    .trim('"')
            },
        )
    }

    @Test
    fun `the raw size is the size of the array before compression`() {
        val batch = TelemetryBatchBuilder.build(payloads)

        assertEquals(TelemetryBatchBuilder.json(batch).toByteArray().size, batch.rawSizeBytes)
    }

    @Test
    fun `the payload starts with the gzip magic number and repeated events compress`() {
        val many = List(200) { """{"type":"AppSession","atMillis":$it}""" }

        val batch = TelemetryBatchBuilder.build(many)

        assertEquals(0x1f, batch.gzippedJson[0].toInt() and 0xff)
        assertEquals(0x8b, batch.gzippedJson[1].toInt() and 0xff)
        assertTrue(batch.gzippedJson.size < batch.rawSizeBytes)
    }

    @Test
    fun `one event makes an array of one`() {
        assertEquals("""[${payloads[0]}]""", TelemetryBatchBuilder.json(TelemetryBatchBuilder.build(payloads.take(1))))
    }
}
