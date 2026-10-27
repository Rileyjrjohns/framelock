package dev.framelock.app

import android.content.Context
import dev.framelock.app.di.appModule
import dev.framelock.app.di.modelsModule
import dev.framelock.live.application.pipeline.DisplaySlot
import dev.framelock.live.domain.policy.ReconnectConfig
import dev.framelock.live.domain.port.MemoryPressureMonitor
import dev.framelock.live.domain.port.ThermalMonitor
import dev.framelock.live.system.SharedMemoryPressureMonitor
import dev.framelock.live.system.SharedThermalMonitor
import dev.framelock.telemetry.domain.port.TelemetrySink
import dev.framelock.telemetry.room.TelemetryUploadRun
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.MissingKoinDefinitionException
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import java.io.File
import java.security.PublicKey
import kotlin.random.Random

@OptIn(KoinExperimentalAPI::class)
class KoinGraphTest {
    @Test
    fun `all production definitions have their constructor dependencies`() {
        appModule.verify(
            // These values are constructed in the definitions, never looked up from the container.
            extraTypes =
                listOf(
                    Context::class,
                    Function0::class,
                    Function1::class,
                    Function2::class,
                    StateFlow::class,
                    DisplaySlot::class,
                    File::class,
                    PublicKey::class,
                    ReconnectConfig::class,
                    Random::class,
                ),
            injections =
                injectedParameters(
                    definition<SharedThermalMonitor>(ThermalMonitor::class),
                    definition<SharedMemoryPressureMonitor>(MemoryPressureMonitor::class),
                ),
        )
    }

    @Test
    fun `an incomplete graph fails verification`() {
        assertThrows(
            MissingKoinDefinitionException::class.java,
        ) { modelsModule.verify(extraTypes = listOf(Context::class)) }
    }
}
