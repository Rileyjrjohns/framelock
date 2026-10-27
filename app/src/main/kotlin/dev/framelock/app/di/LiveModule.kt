package dev.framelock.app.di

import dev.framelock.app.ActiveLabels
import dev.framelock.app.AppLiveCommands
import dev.framelock.app.BackendOverride
import dev.framelock.app.SourceWiring
import dev.framelock.live.application.metrics.MetricsAggregator
import dev.framelock.live.application.pipeline.InferencePipeline
import dev.framelock.live.application.pipeline.ModelLoading
import dev.framelock.live.application.usecase.ObservePipeline
import dev.framelock.live.application.usecase.ReportFrameRendered
import dev.framelock.live.application.usecase.StartPipeline
import dev.framelock.live.application.usecase.StopPipeline
import dev.framelock.live.domain.policy.ReconnectConfig
import dev.framelock.live.domain.policy.ReconnectPolicy
import dev.framelock.live.domain.port.FrameSource
import dev.framelock.live.domain.port.InferenceEngine
import dev.framelock.live.inference.litert.LiteRtInferenceEngine
import dev.framelock.live.system.SharedMemoryPressureMonitor
import dev.framelock.live.system.SharedThermalMonitor
import dev.framelock.live.ui.LiveCommands
import dev.framelock.live.ui.LiveFrameRendering
import dev.framelock.live.ui.LiveSignals
import dev.framelock.live.ui.LiveViewModel
import dev.framelock.models.store.FileModelRepository
import dev.framelock.telemetry.application.TelemetryRecorder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal val liveModule =
    module {
        singleOf(::SourceWiring)
        single<FrameSource> { get<SourceWiring>().source }
        single { LiteRtInferenceEngine(get(), preference = { get<BackendOverride>().preference }) } bind
            InferenceEngine::class
        single { MetricsAggregator(get(), get<TelemetryRecorder>()::record) }
        single { ReconnectPolicy(ReconnectConfig(500.milliseconds, 10.seconds, 0.2), Random.Default) }
        single {
            InferencePipeline(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get<TelemetryRecorder>()::record,
                get(),
                get(),
            )
        } bind ModelLoading::class
        single { StartPipeline(get(), get<FileModelRepository>()::active, get(), get<TelemetryRecorder>()::record) }
        singleOf(::StopPipeline)
        singleOf(::ObservePipeline)
        singleOf(::ReportFrameRendered)
        singleOf(::AppLiveCommands) bind LiveCommands::class
        single {
            val sources = get<SourceWiring>()
            LiveSignals(
                sources.selection.current,
                sources.source.activeDescription,
                get<SharedThermalMonitor>().state,
                get<SharedMemoryPressureMonitor>().state,
                get<LiteRtInferenceEngine>().backend.map { it?.name }.stateIn(get(), SharingStarted.Eagerly, null),
            )
        }
        single {
            LiveFrameRendering(
                get<InferencePipeline>().display,
                get(),
                get(),
                get(),
                get<ActiveLabels>()::labelOf,
            )
        }
        viewModel { parameters -> LiveViewModel(get(), get(), get(), get(), parameters.getOrNull<Boolean>() ?: false) }
    }
