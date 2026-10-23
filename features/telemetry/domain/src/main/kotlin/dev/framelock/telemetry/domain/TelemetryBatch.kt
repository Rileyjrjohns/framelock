package dev.framelock.telemetry.domain

/**
 * What one upload carries: [eventCount] events as a JSON array, gzipped. [rawSizeBytes] is the size of that array
 * before compression, so an uploader can report the ratio. Platform-free: the encoding is the adapter's business.
 */
class TelemetryBatch(
    val eventCount: Int,
    val rawSizeBytes: Int,
    val gzippedJson: ByteArray,
) {
    init {
        require(eventCount > 0) { "A batch holds at least one event" }
        require(rawSizeBytes > 0) { "A batch has a positive raw size" }
        require(gzippedJson.isNotEmpty()) { "A batch has a payload" }
    }
}
