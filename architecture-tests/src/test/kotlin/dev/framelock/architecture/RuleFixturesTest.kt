package dev.framelock.architecture

import dev.framelock.architecture.Rules.ANDROID_PREFIXES
import dev.framelock.architecture.Rules.CORE_IMPORT_ALLOWLIST
import dev.framelock.architecture.Rules.DI_PREFIXES
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Each rule run on a fixture tree (architecture-tests/src/test/fixtures) that breaks it on purpose.
 * Proves the rules can fail, and pins down what they must not flag.
 */
class RuleFixturesTest {
    @Test
    fun `android referenced by its qualified name in core is caught, not when in a comment`() {
        val violations = Rules.forbiddenReferences(Scopes.fixture("android-qualified-in-core"), ANDROID_PREFIXES)

        assertEquals(listOf("android.os.Bundle"), violations.map { it.substringAfter(" references ") })
    }

    @Test
    fun `DI imports and qualified DI annotations are caught in an adapter`() {
        val violations = Rules.forbiddenReferences(Scopes.fixture("di-in-adapter"), DI_PREFIXES)

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
        val violations =
            Rules.importsOutsideAllowlist(
                Scopes.fixture("import-allowlist"),
                CORE_IMPORT_ALLOWLIST.getValue("core/domain"),
            )

        assertEquals(listOf("java.io.InputStreamReader"), violations.map { it.substringAfter(" imports ") })
    }

    @Test
    fun `limitedParallelism is caught in any core module, not in comments or strings`() {
        val violations = Rules.callsToLimitedParallelism(Scopes.fixture("limited-parallelism"))

        assertEquals(1, violations.size, violations.joinToString())
        assertEquals(true, violations.single().contains("/core/domain/"), violations.single())
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
