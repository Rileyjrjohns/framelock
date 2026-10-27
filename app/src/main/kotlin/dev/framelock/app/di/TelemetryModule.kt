package dev.framelock.app.di

import android.util.Log
import dev.framelock.app.AppEnvironment
import dev.framelock.app.TelemetryScheduling
import dev.framelock.telemetry.application.TelemetryRecorder
import dev.framelock.telemetry.application.TrackScreen
import dev.framelock.telemetry.application.TrackUiInteraction
import dev.framelock.telemetry.domain.port.TelemetrySink
import dev.framelock.telemetry.domain.port.TelemetryUploader
import dev.framelock.telemetry.room.LogcatTelemetrySink
import dev.framelock.telemetry.room.LoggingUploader
import dev.framelock.telemetry.room.RoomTelemetryBuffer
import dev.framelock.telemetry.room.RoomTelemetrySink
import dev.framelock.telemetry.room.TeeTelemetrySink
import dev.framelock.telemetry.room.TelemetryBuffer
import dev.framelock.telemetry.room.TelemetryUploadRun
import dev.framelock.telemetry.room.TelemetryWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val telemetryModule =
    module {
        single<TelemetryBuffer> {
            RoomTelemetryBuffer.open(
                get(),
                onDropped = { Log.w(BUFFER_TAG, "Buffer full: the $it oldest events were dropped") },
            )
        }
        single<TelemetrySink> {
            val room =
                RoomTelemetrySink(
                    get(),
                    CoroutineScope(SupervisorJob() + Dispatchers.IO),
                    System::currentTimeMillis,
                    { Log.w(BUFFER_TAG, it) },
                )
            if (get<AppEnvironment>().debuggable) TeeTelemetrySink(room, LogcatTelemetrySink()) else room
        }
        single<TelemetryUploader> {
            LoggingUploader({
                Log.i(UPLOAD_TAG, it)
            }, firstEvents = if (get<AppEnvironment>().debuggable) DEBUG_LOGGED_EVENTS else 0)
        }
        single { TelemetryUploadRun(get(), get(), log = { Log.w(UPLOAD_TAG, it) }) }
        singleOf(::TelemetryRecorder)
        singleOf(::TrackUiInteraction)
        singleOf(::TrackScreen)
        singleOf(::TelemetryWorkerFactory)
        singleOf(::TelemetryScheduling)
    }

private const val BUFFER_TAG = "TelemetryBuffer"
private const val UPLOAD_TAG = "TelemetryUpload"
private const val DEBUG_LOGGED_EVENTS = 5
