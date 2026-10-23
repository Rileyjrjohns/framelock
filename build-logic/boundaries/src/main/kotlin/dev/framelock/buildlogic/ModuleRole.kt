package dev.framelock.buildlogic

import org.gradle.api.GradleException

/** Hexagonal layer of a module (ADR-014). */
enum class Layer { CONTAINER, KERNEL, TESTING, DOMAIN, APPLICATION, ADAPTER, UI, APP, ARCHITECTURE_TESTS }

/**
 * Role of a module, derived from its Gradle path: a layer and, for feature modules, a feature.
 * The allowed dependencies are the rules of ADR-014 ("Layers depend inward", "Features depend on each other in one
 * direction") and ADR-006.
 */
data class ModuleRole(
    val layer: Layer,
    val feature: String? = null,
) {
    /** Pure-Kotlin modules: no Android, no DI, kotlinx-coroutines-core only. */
    val isPure: Boolean
        get() = layer == Layer.KERNEL || layer == Layer.TESTING || layer == Layer.DOMAIN || layer == Layer.APPLICATION

    /** Whether this module may reach [target], directly or transitively, on [classpath]. */
    fun mayReach(
        target: ModuleRole,
        classpath: String,
    ): Boolean {
        val onTest = isTestClasspath(classpath) && layer != Layer.ARCHITECTURE_TESTS
        return reachableInProduction(target) || (onTest && TESTING.reachableInProduction(target)) ||
            (onTest && target.layer == Layer.TESTING)
    }

    private fun reachableInProduction(target: ModuleRole): Boolean {
        val upstream = upstreamOf(feature)
        val ownOrUpstream = target.feature == feature || target.feature in upstream
        return when (layer) {
            Layer.CONTAINER, Layer.KERNEL, Layer.ARCHITECTURE_TESTS -> {
                false
            }

            Layer.TESTING -> {
                target.layer == Layer.KERNEL || target.layer == Layer.DOMAIN
            }

            Layer.DOMAIN -> {
                target.layer == Layer.KERNEL || (target.layer == Layer.DOMAIN && target.feature in upstream)
            }

            Layer.APPLICATION -> {
                target.layer == Layer.KERNEL ||
                    (target.layer == Layer.DOMAIN && ownOrUpstream) ||
                    (target.layer == Layer.APPLICATION && target.feature in upstream)
            }

            Layer.ADAPTER -> {
                target.layer == Layer.KERNEL || (target.layer == Layer.DOMAIN && ownOrUpstream)
            }

            Layer.UI -> {
                target.layer == Layer.KERNEL ||
                    ((target.layer == Layer.DOMAIN || target.layer == Layer.APPLICATION) && ownOrUpstream)
            }

            Layer.APP -> {
                target.layer !in NOT_REACHABLE_FROM_APP
            }
        }
    }

    override fun toString(): String = if (feature == null) layer.name else "${layer.name}($feature)"

    companion object {
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

        /** Features and the features each one may depend on, directly. The graph must stay acyclic. */
        val FEATURE_UPSTREAM: Map<String, Set<String>> =
            mapOf(
                "live" to emptySet(),
                "models" to setOf("live"),
                "telemetry" to setOf("live", "models"),
            )

        private val TESTING = ModuleRole(Layer.TESTING)
        private val NOT_REACHABLE_FROM_APP = setOf(Layer.CONTAINER, Layer.TESTING, Layer.APP, Layer.ARCHITECTURE_TESTS)

        fun of(path: String): ModuleRole {
            val segments = path.removePrefix(":").split(':')
            return when {
                path == ":shared" || path == ":features" -> ModuleRole(Layer.CONTAINER)

                path == ":shared:kernel" -> ModuleRole(Layer.KERNEL)

                path == ":shared:testing" -> ModuleRole(Layer.TESTING)

                path == ":app" -> ModuleRole(Layer.APP)

                path == ":architecture-tests" -> ModuleRole(Layer.ARCHITECTURE_TESTS)

                segments[0] == "features" && segments.size in 2..3 -> featureRole(path, segments)

                else -> throw GradleException(
                    "Module $path has no role. Add it to ModuleRole (build-logic) before using it.",
                )
            }
        }

        private fun featureRole(
            path: String,
            segments: List<String>,
        ): ModuleRole {
            val feature = segments[1]
            if (feature !in FEATURE_UPSTREAM) {
                throw GradleException(
                    "Module $path belongs to unknown feature '$feature'. Declare it in FEATURE_UPSTREAM.",
                )
            }
            val layer =
                when (segments.getOrNull(2)) {
                    null -> Layer.CONTAINER
                    "domain" -> Layer.DOMAIN
                    "application" -> Layer.APPLICATION
                    "ui" -> Layer.UI
                    else -> Layer.ADAPTER
                }
            return ModuleRole(layer, feature)
        }

        /** Every feature [feature] depends on, directly or not. */
        fun upstreamOf(feature: String?): Set<String> {
            val result = mutableSetOf<String>()
            val pending = ArrayDeque(FEATURE_UPSTREAM[feature].orEmpty())
            while (pending.isNotEmpty()) {
                val next = pending.removeFirst()
                if (result.add(next)) pending += FEATURE_UPSTREAM[next].orEmpty()
            }
            return result
        }

        fun isClasspath(configuration: String): Boolean = CLASSPATH.matches(configuration)

        fun isTestClasspath(configuration: String): Boolean = TEST_CLASSPATH.matches(configuration)

        /** External libraries allowed on the production classpaths of pure modules: kotlinx-coroutines-core and Kotlin. */
        fun isAllowedInPure(module: String): Boolean =
            module.startsWith("org.jetbrains.kotlin:kotlin-stdlib") ||
                module.startsWith("org.jetbrains.kotlinx:kotlinx-coroutines-core") ||
                module == "org.jetbrains.kotlinx:kotlinx-coroutines-bom" ||
                module == "org.jetbrains:annotations"

        fun isDependencyInjection(module: String): Boolean = module.substringBefore(':') in DI_GROUPS
    }
}
