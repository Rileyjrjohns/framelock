package dev.framelock.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The same rule calls as [LayeringRulesTest], run on fixture trees (architecture-tests/src/test/fixtures) that
 * break them on purpose. Proves each rule and its scope can fail, and pins down what they must not flag.
 */
class RuleFixturesTest {
    @Test
    fun `android referenced by its qualified name in a pure module is caught, not when in a comment`() {
        val violations = Rules.pureReferencesToAndroid(Scopes.fixture("android-qualified-in-pure-module"))

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
        val violations = Rules.pureImportsOutsideAllowlist(Scopes.fixture("import-allowlist"))

        assertEquals(listOf("java.io.InputStreamReader"), violations.map { it.substringAfter(" imports ") })
    }

    @Test
    fun `limitedParallelism is caught in any pure module, not in comments or strings`() {
        val violations = Rules.pureCallsToLimitedParallelism(Scopes.fixture("limited-parallelism"))

        assertEquals(1, violations.size, violations.joinToString())
        assertEquals(true, violations.single().contains("/features/live/domain/"), violations.single())
    }

    @Test
    fun `port implementations outside adapters are caught however they are written`() {
        val violations = Rules.portViolations(Scopes.fixture("ports"))

        val port = "dev.framelock.live.domain.port"
        assertEquals(
            setOf(
                "Port dev.framelock.live.domain.FrameSource must be declared in a port package",
                "Port $port.TelemetrySink must not be a fun interface",
                "dev.framelock.live.application.QualifiedThermal implements port $port.ThermalMonitor outside adapters",
                "dev.framelock.live.application.StarMemory implements port $port.MemoryPressureMonitor outside adapters",
                "Object expression implements port $port.Tracer outside adapters",
                "Object expression implements port $port.Clock outside adapters",
                "dev.framelock.app.AppDeviceInfo implements port $port.DeviceInfoProvider outside adapters",
                "dev.framelock.app.IndirectDeviceInfo implements port $port.DeviceInfoProvider outside adapters",
                "dev.framelock.app.AliasedClock implements port $port.Clock outside adapters",
                "dev.framelock.app.DebugTracer implements port $port.Tracer outside adapters",
                "dev.framelock.app.OtherSource implements port $port.FrameSource outside adapters",
            ),
            violations.map { it.substringBefore(" (") }.toSet(),
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `telemetry fields that could carry an image or free text are caught`() {
        val violations = Rules.telemetryFieldViolations(Scopes.fixture("telemetry-fields"))

        assertEquals(
            setOf(
                "dev.framelock.live.domain.telemetry.LeakyEvent.note",
                "dev.framelock.live.domain.telemetry.LeakyEvent.frame",
            ),
            violations.map { it.substringBefore(" has type") }.toSet(),
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `hidden directories are skipped only at the repository root`() {
        assertEquals(false, Scopes.isProjectSource("/.worktrees/x/features/live/domain/src/main/kotlin/A.kt"))
        assertEquals(true, Scopes.isProjectSource("/features/live/domain/src/main/kotlin/dev/.hidden/A.kt"))
    }
}
