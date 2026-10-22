package dev.framelock.architecture

import dev.framelock.architecture.Rules.ADAPTER_MODULES
import dev.framelock.architecture.Rules.ANDROID_PREFIXES
import dev.framelock.architecture.Rules.CORE_IMPORT_ALLOWLIST
import dev.framelock.architecture.Rules.CORE_MODULES
import dev.framelock.architecture.Rules.DI_PREFIXES
import dev.framelock.architecture.Rules.ROOT_PACKAGE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Rules from docs/architecture.md ("Principles", "Modules"), ADR-002 and ADR-006, run on the repository. */
class LayeringRulesTest {
    private val adapters = ADAPTER_MODULES.map { "adapters/$it" }

    @Test
    fun `every module scope is non-empty`() {
        (CORE_MODULES + adapters + "app").forEach { Scopes.production(it) }
    }

    @Test
    fun `core does not reference android, androidx or com google android`() {
        assertNoViolation(Rules.forbiddenReferences(CORE_MODULES.flatMap { Scopes.production(it) }, ANDROID_PREFIXES))
    }

    @Test
    fun `no module references a DI framework or DI annotations`() {
        val modules = CORE_MODULES + adapters + "app"
        assertNoViolation(Rules.forbiddenReferences(modules.flatMap { Scopes.all(it) }, DI_PREFIXES))
    }

    @Test
    fun `core production imports only kotlin, kotlinx coroutines and the per-module allowlist`() {
        assertNoViolation(
            CORE_IMPORT_ALLOWLIST.flatMap { (module, allowed) ->
                Rules.importsOutsideAllowlist(Scopes.production(module), allowed)
            },
        )
    }

    @Test
    fun `domain does not reference application`() {
        assertNoViolation(
            Rules.forbiddenReferences(Scopes.production("core/domain"), listOf("$ROOT_PACKAGE.core.application.")),
        )
    }

    @Test
    fun `adapters do not reference another adapter's package`() {
        assertNoViolation(
            ADAPTER_MODULES.flatMap { adapter ->
                val others =
                    ADAPTER_MODULES.filter { it != adapter }.map {
                        "$ROOT_PACKAGE.adapters.${it.replace(
                            '-',
                            '.',
                        )}."
                    }
                Rules.forbiddenReferences(Scopes.all("adapters/$adapter"), others)
            },
        )
    }

    @Test
    fun `adapters do not reference core application`() {
        assertNoViolation(
            Rules.forbiddenReferences(
                adapters.flatMap { Scopes.production(it) },
                listOf("$ROOT_PACKAGE.core.application."),
            ),
        )
    }

    @Test
    fun `ports are implemented only in adapters, core testing or test sources`() {
        assertNoViolation(Rules.portViolations(Scopes.project))
    }

    @Test
    fun `core does not call limitedParallelism`() {
        assertNoViolation(Rules.callsToLimitedParallelism(CORE_MODULES.flatMap { Scopes.production(it) }))
    }

    private fun assertNoViolation(violations: List<String>) {
        assertEquals(emptyList<String>(), violations, violations.joinToString("\n"))
    }
}
