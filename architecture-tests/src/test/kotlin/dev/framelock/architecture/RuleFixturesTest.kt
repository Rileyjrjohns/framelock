package dev.framelock.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The same rule calls as [LayeringRulesTest], run on fixture trees (architecture-tests/src/test/fixtures) that
 * break them on purpose. Proves each rule and its scope can fail, and pins down what they must not flag.
 */
class RuleFixturesTest {
    @Test
    fun `android referenced by its qualified name in core is caught, not when in a comment`() {
        val violations = Rules.coreReferencesToAndroid(Scopes.fixture("android-qualified-in-core"))

        assertEquals(listOf("android.os.Bundle"), violations.map { it.substringAfter(" references ") })
    }

    @Test
    fun `DI imports and qualified DI annotations are caught in an adapter`() {
        val violations = Rules.referencesToDependencyInjection(Scopes.fixture("di-in-adapter"))

        assertEquals(
            setOf("dagger.Module", "javax.inject.Inject"),
            violations
                .map {
                    it.substringAfter(" references ")
                }.toSet(),
        )
    }

    @Test
    fun `allowlisted class name does not allow classes sharing its prefix`() {
        val violations = Rules.coreImportsOutsideAllowlist(Scopes.fixture("import-allowlist"))

        assertEquals(listOf("java.io.InputStreamReader"), violations.map { it.substringAfter(" imports ") })
    }

    @Test
    fun `limitedParallelism is caught in any core module, not in comments or strings`() {
        val violations = Rules.coreCallsToLimitedParallelism(Scopes.fixture("limited-parallelism"))

        assertEquals(1, violations.size, violations.joinToString())
        assertEquals(true, violations.single().contains("/core/domain/"), violations.single())
    }

    @Test
    fun `layering references between domain, application and adapters are caught`() {
        val files = Scopes.fixture("layering")

        assertEquals(
            1,
            Rules.domainReferencesToApplication(files).size,
            Rules.domainReferencesToApplication(files).joinToString(),
        )
        assertEquals(
            1,
            Rules.adapterReferencesToOtherAdapters(files).size,
            Rules.adapterReferencesToOtherAdapters(files).joinToString(),
        )
        assertEquals(
            1,
            Rules.adapterReferencesToApplication(files).size,
            Rules.adapterReferencesToApplication(files).joinToString(),
        )
    }

    @Test
    fun `port implementations outside adapters are caught however they are written`() {
        val violations = Rules.portViolations(Scopes.fixture("ports"))

        val port = "dev.framelock.core.domain.port"
        assertEquals(
            setOf(
                "Port dev.framelock.core.domain.FrameSource must be declared in $port",
                "Port $port.TelemetrySink must not be a fun interface",
                "dev.framelock.core.application.QualifiedThermal implements port $port.ThermalMonitor outside adapters",
                "dev.framelock.core.application.StarMemory implements port $port.MemoryPressureMonitor outside adapters",
                "Object expression implements port $port.Tracer outside adapters",
                "Object expression implements port $port.Clock outside adapters",
                "dev.framelock.app.AppDeviceInfo implements port $port.DeviceInfoProvider outside adapters",
                "dev.framelock.app.IndirectDeviceInfo implements port $port.DeviceInfoProvider outside adapters",
                "dev.framelock.app.AliasedClock implements port $port.Clock outside adapters",
                "dev.framelock.app.DebugTracer implements port $port.Tracer outside adapters",
            ),
            violations.map { it.substringBefore(" (") }.toSet(),
            violations.joinToString("\n"),
        )
    }
}
