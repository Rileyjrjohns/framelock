package dev.framelock.buildlogic

import org.gradle.api.GradleException

/**
 * Role of a module in the hexagonal layout, derived from its Gradle path.
 * The allowed dependencies of each role are the rules of docs/architecture.md ("Modules").
 */
enum class ModuleRole {
    /** Implicit parent projects such as `:core` and `:adapters`: no sources, no dependencies. */
    CONTAINER,
    CORE_DOMAIN,
    CORE_APPLICATION,
    CORE_TESTING,
    ADAPTER,
    APP,
    ARCHITECTURE_TESTS,
    ;

    val isCore: Boolean
        get() = this == CORE_DOMAIN || this == CORE_APPLICATION || this == CORE_TESTING

    /** Returns why [target] is not allowed on [configuration], or null if it is allowed. */
    fun projectDependencyViolation(
        configuration: String,
        target: String,
    ): String? {
        val targetRole = of(target)
        val allowedOnMain =
            when (this) {
                CONTAINER, CORE_DOMAIN, ARCHITECTURE_TESTS -> emptySet()
                CORE_APPLICATION, CORE_TESTING, ADAPTER -> setOf(CORE_DOMAIN)
                APP -> setOf(CORE_DOMAIN, CORE_APPLICATION, ADAPTER)
            }
        val allowed =
            if (isTestConfiguration(configuration) && this != ARCHITECTURE_TESTS) {
                allowedOnMain + CORE_TESTING
            } else {
                allowedOnMain
            }
        return if (targetRole in allowed) {
            null
        } else {
            "$name may not depend on $target ($targetRole) through '$configuration'"
        }
    }

    companion object {
        private const val ADAPTERS_PREFIX = ":adapters:"

        fun of(path: String): ModuleRole =
            when {
                path == ":core" || path == ":adapters" -> CONTAINER

                path == ":core:domain" -> CORE_DOMAIN

                path == ":core:application" -> CORE_APPLICATION

                path == ":core:testing" -> CORE_TESTING

                path.startsWith(ADAPTERS_PREFIX) && ':' !in path.removePrefix(ADAPTERS_PREFIX) -> ADAPTER

                path == ":app" -> APP

                path == ":architecture-tests" -> ARCHITECTURE_TESTS

                else -> throw GradleException(
                    "Module $path has no role. Add it to ModuleRole (build-logic) before using it.",
                )
            }

        /** `testImplementation`, `androidTestImplementation`, `kspTest`, `debugUnitTestRuntimeClasspath`, ... */
        fun isTestConfiguration(name: String): Boolean =
            name.startsWith("test") || name.startsWith("androidTest") || name.contains("Test")

        /** External libraries allowed on the classpaths of `:core:*` (only kotlinx-coroutines-core, plus Kotlin itself). */
        fun isAllowedInCore(module: String): Boolean =
            module.startsWith("org.jetbrains.kotlin:kotlin-stdlib") ||
                module.startsWith("org.jetbrains.kotlinx:kotlinx-coroutines-core") ||
                module == "org.jetbrains.kotlinx:kotlinx-coroutines-bom" ||
                module == "org.jetbrains:annotations"
    }
}
