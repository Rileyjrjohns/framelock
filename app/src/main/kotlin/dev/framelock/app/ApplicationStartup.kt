package dev.framelock.app

import dev.framelock.kernel.telemetry.UsageTelemetry
import dev.framelock.telemetry.application.TelemetryRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class ApplicationStartup(
    private val telemetry: TelemetryRecorder,
    private val models: ModelStartup,
    private val scheduling: TelemetryScheduling,
    private val scope: CoroutineScope,
) {
    fun start() {
        telemetry.record(UsageTelemetry.AppSession)
        scope.launch(Dispatchers.IO) { models.run() }
        scheduling.start()
    }
}
