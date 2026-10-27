package dev.framelock.app

import android.content.Context
import androidx.work.WorkManager
import dev.framelock.telemetry.room.TelemetryUploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration

/** Scheduling starts only after the application's worker configuration is available. */
internal class TelemetryScheduling(
    private val context: Context,
    private val environment: AppEnvironment,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch(Dispatchers.IO) {
            val scheduler = TelemetryUploadScheduler(WorkManager.getInstance(context))
            scheduler.schedulePeriodic()
            if (environment.debuggable) scheduler.enqueueOnce(Duration.ofSeconds(DEBUG_UPLOAD_DELAY_SECONDS))
        }
    }

    private companion object {
        const val DEBUG_UPLOAD_DELAY_SECONDS = 20L
    }
}
