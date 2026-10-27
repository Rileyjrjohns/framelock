package dev.framelock.app.di

import android.content.Context
import android.util.Log
import dev.framelock.app.ActiveLabels
import dev.framelock.app.AppModelActions
import dev.framelock.app.ModelAvailability
import dev.framelock.app.ModelListing
import dev.framelock.app.ModelStartup
import dev.framelock.app.TrustedKeyAssets
import dev.framelock.models.application.ActivateModel
import dev.framelock.models.application.ImportModel
import dev.framelock.models.application.RollbackModel
import dev.framelock.models.domain.port.AuditLog
import dev.framelock.models.domain.port.ModelRepository
import dev.framelock.models.store.FileAuditLog
import dev.framelock.models.store.FileModelRepository
import dev.framelock.models.ui.ModelsActions
import dev.framelock.models.ui.ModelsViewModel
import dev.framelock.telemetry.application.TelemetryRecorder
import dev.framelock.telemetry.application.TrackUiInteraction
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

internal val modelsModule =
    module {
        single {
            FileModelRepository(
                get<Context>().filesDir,
                TrustedKeyAssets.load(
                    get<Context>().assets,
                ) { name, error -> Log.w("ModelStore", "Trusted key $name ignored", error) },
            )
        } bind ModelRepository::class
        single { FileAuditLog(get<Context>().filesDir) } bind AuditLog::class
        single { ImportModel(get(), get(), get(), get<TelemetryRecorder>()::record, get()) }
        single { ActivateModel(get(), get(), get(), get<TelemetryRecorder>()::record, get()) }
        single { RollbackModel(get(), get(), get(), get<TelemetryRecorder>()::record, get()) }
        single {
            val repository = get<FileModelRepository>()
            ActiveLabels(
                { repository.active()?.manifest?.version },
                repository::labels,
                { Log.w("ModelLabels", "Labels unreadable; the overlay shows class numbers", it) },
            )
        }
        singleOf(::ModelStartup)
        singleOf(::ModelAvailability)
        singleOf(::ModelListing)
        singleOf(::AppModelActions) bind ModelsActions::class
        viewModel { ModelsViewModel(get(), get<ModelAvailability>().canSwap, { get<TrackUiInteraction>()(it) }) }
    }
