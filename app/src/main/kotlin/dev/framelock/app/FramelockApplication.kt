package dev.framelock.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.work.Configuration

/**
 * WorkManager's default initializer is removed in the manifest; this class is its configuration provider, so the
 * telemetry worker is built from the app graph (manual DI).
 */
class FramelockApplication :
    Application(),
    Configuration.Provider {
    val graph: AppGraph by lazy { AppGraph(this) }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(graph.workerFactory)
                .setMinimumLoggingLevel(
                    if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE !=
                        0
                    ) {
                        Log.INFO
                    } else {
                        Log.ERROR
                    },
                ).build()

    override fun onCreate() {
        super.onCreate()
        graph.scheduleTelemetryUpload()
    }
}
