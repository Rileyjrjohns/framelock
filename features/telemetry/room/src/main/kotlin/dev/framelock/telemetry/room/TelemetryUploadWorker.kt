package dev.framelock.telemetry.room

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/**
 * Runs [TelemetryUploadRun] in the background (ADR-004, M6). A failed upload asks for a retry, with the exponential
 * backoff set when the work is scheduled; the events stay buffered until an upload succeeds.
 */
class TelemetryUploadWorker(
    context: Context,
    params: WorkerParameters,
    private val run: TelemetryUploadRun,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            when (run.run()) {
                TelemetryUploadRun.Outcome.DRAINED, TelemetryUploadRun.Outcome.LIMIT_REACHED -> Result.success()
                TelemetryUploadRun.Outcome.FAILED -> Result.retry()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // A buffer error (disk full, corrupt database): the events are still there, try again later.
            Result.retry()
        }
}

/** Builds [TelemetryUploadWorker] from the app graph; WorkManager's default factory cannot (manual DI). */
class TelemetryWorkerFactory(
    private val run: TelemetryUploadRun,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == TelemetryUploadWorker::class.java.name) {
            TelemetryUploadWorker(appContext, workerParameters, run)
        } else {
            null
        }
}
