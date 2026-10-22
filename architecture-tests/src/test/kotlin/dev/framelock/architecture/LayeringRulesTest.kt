package dev.framelock.architecture

import dev.framelock.architecture.Rules.ADAPTER_MODULES
import dev.framelock.architecture.Rules.CORE_MODULES
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Rules from docs/architecture.md ("Principles", "Modules"), ADR-002 and ADR-006, run on the repository.
 * Each rule picks its own scope (see [Rules]); [RuleFixturesTest] proves the same calls fail on broken fixtures.
 */
class LayeringRulesTest {
    private val files = Scopes.project

    @Test
    fun `every module scope is non-empty`() {
        (CORE_MODULES + ADAPTER_MODULES.map { "adapters/$it" } + "app").forEach { Scopes.production(it) }
    }

    @Test
    fun `core does not reference android, androidx or com google android`() {
        assertNoViolation(Rules.coreReferencesToAndroid(files))
    }

    @Test
    fun `no module references a DI framework or DI annotations`() {
        assertNoViolation(Rules.referencesToDependencyInjection(files))
    }

    @Test
    fun `core production imports only kotlin, kotlinx coroutines and the per-module allowlist`() {
        assertNoViolation(Rules.coreImportsOutsideAllowlist(files))
    }

    @Test
    fun `domain does not reference application`() {
        assertNoViolation(Rules.domainReferencesToApplication(files))
    }

    @Test
    fun `adapters do not reference another adapter's package`() {
        assertNoViolation(Rules.adapterReferencesToOtherAdapters(files))
    }

    @Test
    fun `adapters do not reference core application`() {
        assertNoViolation(Rules.adapterReferencesToApplication(files))
    }

    @Test
    fun `ports are implemented only in adapters, core testing or test sources`() {
        assertNoViolation(Rules.portViolations(files))
    }

    @Test
    fun `core does not use limitedParallelism`() {
        assertNoViolation(Rules.coreCallsToLimitedParallelism(files))
    }

    private fun assertNoViolation(violations: List<String>) {
        assertEquals(emptyList<String>(), violations, violations.joinToString("\n"))
    }
}
