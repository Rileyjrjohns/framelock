package dev.framelock.buildlogic

import org.gradle.api.GradleException

/**
 * Role of a module in the hexagonal layout, derived from its Gradle path.
 * The allowed dependencies of each role are the rules of docs/architecture.md ("Modules") and ADR-006.
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

    /**
     * Roles this module may reach, directly or transitively, on a classpath.
     * Test classpaths may additionally reach `:core:testing`.
     */
    fun allowedOn(classpath: String): Set<ModuleRole> {
        val production =
            when (this) {
                CONTAINER, CORE_DOMAIN, ARCHITECTURE_TESTS -> emptySet()
                CORE_APPLICATION, CORE_TESTING, ADAPTER -> setOf(CORE_DOMAIN)
                APP -> setOf(CORE_DOMAIN, CORE_APPLICATION, ADAPTER)
            }
        return if (isTestClasspath(classpath) && this != ARCHITECTURE_TESTS) production + CORE_TESTING else production
    }

    companion object {
        private const val ADAPTERS_PREFIX = ":adapters:"

        /**
         * Test classpaths, recognised by their exact shape: `testCompileClasspath`, `testRuntimeClasspath` (JVM) and
         * `<variant>UnitTest…Classpath` / `<variant>AndroidTest…Classpath` (AGP). Every other classpath, including
         * `testFixtures…` and flavors whose name merely contains "Test", is treated as production.
         */
        private val TEST_CLASSPATH =
            Regex(
                "^test(Compile|Runtime)Classpath$|^[a-z][A-Za-z0-9]*(UnitTest|AndroidTest)(Compile|Runtime)Classpath$",
            )

        /** Classpath configurations checked by `verifyModuleBoundaries`. */
        private val CLASSPATH = Regex("^(compile|runtime)Classpath$|^[a-z][A-Za-z0-9]*(Compile|Runtime)Classpath$")

        /** Libraries forbidden on every production classpath: manual DI only (no Hilt, no Koin, no DI annotations). */
        private val DI_GROUPS =
            setOf("com.google.dagger", "io.insert-koin", "javax.inject", "jakarta.inject", "com.google.inject")

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

        fun isClasspath(configuration: String): Boolean = CLASSPATH.matches(configuration)

        fun isTestClasspath(configuration: String): Boolean = TEST_CLASSPATH.matches(configuration)

        /** External libraries allowed on the production classpaths of `:core:*`: kotlinx-coroutines-core and Kotlin. */
        fun isAllowedInCore(module: String): Boolean =
            module.startsWith("org.jetbrains.kotlin:kotlin-stdlib") ||
                module.startsWith("org.jetbrains.kotlinx:kotlinx-coroutines-core") ||
                module == "org.jetbrains.kotlinx:kotlinx-coroutines-bom" ||
                module == "org.jetbrains:annotations"

        fun isDependencyInjection(module: String): Boolean = module.substringBefore(':') in DI_GROUPS
    }
}
