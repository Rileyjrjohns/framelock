package dev.framelock.telemetry.room

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

/** Schedules [TelemetryUploadWorker] (ADR-004, M6). Both kinds of work need a network connection. */
class TelemetryUploadScheduler(
    private val workManager: WorkManager,
) {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Unique periodic work, every 15 minutes (WorkManager's minimum); an existing schedule is kept. */
    fun schedulePeriodic() {
        val request =
            PeriodicWorkRequestBuilder<TelemetryUploadWorker>(PERIOD)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
                .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One upload after [delay], so a device check does not wait for the period. Debug builds only. */
    fun enqueueOnce(delay: Duration) {
        val request =
            OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delay)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
                .build()
        workManager.enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val PERIODIC_NAME = "telemetry-upload"
        const val ONE_TIME_NAME = "telemetry-upload-once"
        private val PERIOD: Duration = Duration.ofMinutes(15)
        private val BACKOFF: Duration = Duration.ofSeconds(30)
    }
}
