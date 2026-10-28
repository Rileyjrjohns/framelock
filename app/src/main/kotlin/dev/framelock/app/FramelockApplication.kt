package dev.framelock.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.work.Configuration
import dev.framelock.app.di.appModule
import dev.framelock.telemetry.room.TelemetryWorkerFactory
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * WorkManager's default initializer is removed in the manifest; this class is its configuration provider, so the
 * telemetry worker is built from the application container.
 */
class FramelockApplication :
    Application(),
    Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(get<TelemetryWorkerFactory>())
                .setMinimumLoggingLevel(
                    if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE !=
                        0
                    ) {
                        Log.INFO
                    } else {
                        Log.ERROR
                    },
                ).build()

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        get<SourceWiring>().refreshLabels()
    }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FramelockApplication)
            modules(appModule)
        }
        get<ApplicationStartup>().start()
    }
}
