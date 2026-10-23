package dev.framelock.core.testing

import dev.framelock.core.domain.port.TelemetrySink
import dev.framelock.core.domain.telemetry.TelemetryEvent
import java.util.concurrent.CopyOnWriteArrayList

/** A [TelemetrySink] that keeps what it receives. Thread-safe. */
class FakeTelemetrySink : TelemetrySink {
    private val recorded = CopyOnWriteArrayList<TelemetryEvent>()

    @Volatile
    private var failure: RuntimeException? = null

    /** Events accepted so far, in order. */
    val events: List<TelemetryEvent> get() = recorded.toList()

    /** Makes [record] throw [error] and drop its event, until called again with null. */
    fun failWith(error: RuntimeException?) {
        failure = error
    }

    override fun record(event: TelemetryEvent) {
        failure?.let { throw it }
        recorded += event
    }
}
