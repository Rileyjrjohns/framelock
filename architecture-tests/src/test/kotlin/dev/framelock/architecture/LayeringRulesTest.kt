package dev.framelock.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.framelock.architecture.Scopes.ADAPTER_MODULES
import dev.framelock.architecture.Scopes.CORE_MODULES
import dev.framelock.architecture.Scopes.ROOT_PACKAGE
import org.junit.jupiter.api.Test

/** Rules from docs/architecture.md ("Principles", "Modules"). */
class LayeringRulesTest {
    @Test
    fun `every module scope is non-empty`() {
        (CORE_MODULES + ADAPTER_MODULES.map { "adapters/$it" } + "app").forEach { Scopes.mainFiles(it) }
    }

    @Test
    fun `core does not import android, androidx or com google android`() {
        coreMainFiles().assertFalse { file ->
            file.imports.any { import -> ANDROID_PREFIXES.any { import.name.startsWith(it) } }
        }
    }

    @Test
    fun `core uses no DI annotations`() {
        coreMainFiles().assertFalse { file ->
            file.imports.any { import -> DI_PREFIXES.any { import.name.startsWith(it) } }
        }
    }

    @Test
    fun `core main imports only kotlin, kotlinx coroutines and the per-module JDK allowlist`() {
        for ((module, allowed) in CORE_IMPORT_ALLOWLIST) {
            Scopes.mainFiles(module).assertTrue(additionalMessage = "Allowed in $module: $allowed") { file ->
                file.imports.all { import -> allowed.any { import.name.startsWith(it) } }
            }
        }
    }

    @Test
    fun `domain does not import application`() {
        Scopes.mainFiles("core/domain").assertFalse { file ->
            file.imports.any { it.name.startsWith("$ROOT_PACKAGE.core.application") }
        }
    }

    @Test
    fun `adapters do not import another adapter's package`() {
        for (adapter in ADAPTER_MODULES) {
            val others = ADAPTER_MODULES.filter { it != adapter }.map { Scopes.adapterPackage(it) + "." }
            Scopes.allFiles("adapters/$adapter").assertFalse(additionalMessage = "in adapters/$adapter") { file ->
                file.imports.any { import -> others.any { import.name.startsWith(it) } }
            }
        }
    }

    @Test
    fun `adapters do not import core application`() {
        for (adapter in ADAPTER_MODULES) {
            Scopes.mainFiles("adapters/$adapter").assertFalse { file ->
                file.imports.any { it.name.startsWith("$ROOT_PACKAGE.core.application") }
            }
        }
    }

    @Test
    fun `ports are implemented only in adapters, core testing or test sources`() {
        val portNames =
            Konsist
                .scopeFromPackage("$ROOT_PACKAGE.core.domain.port..")
                .interfaces()
                .map { it.name }
        if (portNames.isEmpty()) return // M0: no port yet.
        val forbiddenImplementers =
            CORE_MODULES.filter { it != "core/testing" }.flatMap { Scopes.mainFiles(it) } + Scopes.mainFiles("app")
        forbiddenImplementers.flatMap { it.classesAndObjects() }.assertFalse { declaration ->
            declaration.hasParentInterfaceWithName(portNames)
        }
    }

    @Test
    fun `pipeline does not call limitedParallelism`() {
        Scopes.mainFiles("core/application").assertFalse { it.text.contains("limitedParallelism") }
    }

    @Test
    fun `app uses no Hilt or Koin`() {
        Scopes.allFiles("app").assertFalse { file ->
            file.imports.any { import -> DI_PREFIXES.any { import.name.startsWith(it) } }
        }
    }

    private fun coreMainFiles(): List<KoFileDeclaration> = CORE_MODULES.flatMap { Scopes.mainFiles(it) }

    private companion object {
        val ANDROID_PREFIXES = listOf("android.", "androidx.", "com.google.android.")
        val DI_PREFIXES =
            listOf("javax.inject.", "jakarta.inject.", "dagger.", "org.koin.", "com.google.inject.")

        private val KOTLIN = listOf("kotlin.", "kotlinx.coroutines.")

        /** Decision D-22: JDK imports allowed per core module. Extend through a reviewed change only. */
        val CORE_IMPORT_ALLOWLIST =
            mapOf(
                "core/domain" to KOTLIN + listOf("$ROOT_PACKAGE.core.domain.", "java.io.InputStream"),
                "core/application" to KOTLIN + listOf("$ROOT_PACKAGE.core.domain.", "$ROOT_PACKAGE.core.application."),
                "core/testing" to
                    KOTLIN +
                    listOf("$ROOT_PACKAGE.core.domain.", "$ROOT_PACKAGE.core.testing.", "java.util.concurrent."),
            )
    }
}
