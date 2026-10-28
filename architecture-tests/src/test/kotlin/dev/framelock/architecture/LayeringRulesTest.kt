package dev.framelock.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Rules from docs/architecture.md ("Principles"), ADR-002, ADR-006 and ADR-014, run on the repository.
 * Each rule picks its own scope (see [Rules]); [RuleFixturesTest] proves the same calls fail on broken fixtures.
 */
class LayeringRulesTest {
    private val files = Scopes.project

    @Test
    fun `every module scope is non-empty`() {
        Rules.MODULES.forEach { Scopes.production(it) }
    }

    @Test
    fun `pure modules do not reference android, androidx or com google android`() {
        assertNoViolation(Rules.pureReferencesToAndroid(files))
    }

    @Test
    fun `only app may reference Koin and all other DI stays forbidden`() {
        assertNoViolation(Rules.referencesToDependencyInjection(files))
    }

    @Test
    fun `pure modules import only kotlin, kotlinx coroutines, the project and their JDK allowlist`() {
        assertNoViolation(Rules.pureImportsOutsideAllowlist(files))
    }

    @Test
    fun `ports are implemented only in adapters, shared testing or test sources`() {
        assertNoViolation(Rules.portViolations(files))
    }

    @Test
    fun `telemetry events carry only primitives, enums, value classes and bounded strings`() {
        assertNoViolation(Rules.telemetryFieldViolations(files))
    }

    @Test
    fun `pure modules do not use limitedParallelism`() {
        assertNoViolation(Rules.pureCallsToLimitedParallelism(files))
    }

    @Test
    fun `feature UI uses design system primitives`() = assertNoViolation(UiRules.materialImports(files))

    @Test
    fun `public UI declarations have one correctly named file each`() = assertNoViolation(UiRules.declarations(files))

    @Test
    fun `feature UI files stay within 400 lines`() = assertNoViolation(UiRules.fileLength(files))

    @Test
    fun `feature UI has no literal visible text`() = assertNoViolation(UiRules.literalText(files))

    @Test
    fun `feature UI uses dimension and colour tokens`() = assertNoViolation(UiRules.literalTokens(files))

    private fun assertNoViolation(violations: List<String>) {
        assertEquals(emptyList<String>(), violations, violations.joinToString("\n"))
    }
}
