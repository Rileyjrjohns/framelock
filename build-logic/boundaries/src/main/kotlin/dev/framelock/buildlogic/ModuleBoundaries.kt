package dev.framelock.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/** Settings plugin: every project except the root gets [ModuleBoundariesPlugin], so no module can opt out. */
class ModuleBoundariesSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        settings.gradle.lifecycle.beforeProject {
            if (path != ":") pluginManager.apply(ModuleBoundariesPlugin::class.java)
        }
    }
}

/**
 * Registers `verifyModuleBoundaries` and makes `check` depend on it.
 *
 * The check works on **resolved** classpaths, transitively, not on declared dependencies: whatever way a dependency
 * gets there (substitution, `extendsFrom`, a test fixture, a transitive edge), what ends up on a classpath is checked.
 */
class ModuleBoundariesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val role = ModuleRole.of(project.path)
        val verify =
            project.tasks.register<VerifyModuleBoundaries>("verifyModuleBoundaries") {
                group = "verification"
                description = "Fails if this module reaches a module or library its role forbids."
                modulePath.set(project.path)
            }
        project.configurations.configureEach {
            val configuration = this
            if (isCanBeResolved && ModuleRole.isClasspath(name)) {
                val root = incoming.resolutionResult.rootComponent
                verify.configure { reached.addAll(root.map { reachedComponents(configuration.name, it) }) }
            }
            if (role.isPure) {
                // A jar added with files(...) has no component identity, so the classpath walk cannot see it.
                verify.configure {
                    fileDependencies.addAll(
                        project.provider {
                            configuration.dependencies.withType(FileCollectionDependency::class.java).map {
                                configuration.name
                            }
                        },
                    )
                }
            }
        }
        project.plugins.withType<BasePlugin> {
            project.tasks.named("check") { dependsOn(verify) }
        }
    }

    /**
     * Entries `classpath|project|:path`, `classpath|foreign-project|:build:path`, and `classpath|module|group:name`
     * or `classpath|direct-module|group:name` for a library declared by the module itself (a direct edge).
     */
    private fun reachedComponents(
        classpath: String,
        root: ResolvedComponentResult,
    ): List<String> {
        val seen = mutableSetOf<ResolvedComponentResult>()
        val pending = ArrayDeque(listOf(root))
        val entries = sortedSetOf<String>()
        val direct =
            root.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .map { it.selected }
                .toSet()
        while (pending.isNotEmpty()) {
            val component = pending.removeFirst()
            if (!seen.add(component)) continue
            if (component !== root) {
                when (val id = component.id) {
                    is ProjectComponentIdentifier -> {
                        entries +=
                            if (id.buildTreePath == id.projectPath) {
                                "$classpath|project|${id.projectPath}"
                            } else {
                                "$classpath|foreign-project|${id.buildTreePath}"
                            }
                    }

                    is ModuleComponentIdentifier -> {
                        val kind = if (component in direct) "direct-module" else "module"
                        entries += "$classpath|$kind|${id.group}:${id.module}"
                    }
                }
            }
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { pending += it.selected }
        }
        return entries.toList()
    }
}

abstract class VerifyModuleBoundaries : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    /** Components reached on each classpath, see [ModuleBoundariesPlugin]. */
    @get:Input
    abstract val reached: ListProperty<String>

    /** Configurations of a pure module that declare a `files(...)` dependency. */
    @get:Input
    abstract val fileDependencies: ListProperty<String>

    @TaskAction
    fun verify() {
        val path = modulePath.get()
        val role = ModuleRole.of(path)
        val violations = mutableListOf<String>()
        for (entry in reached.get()) {
            val (classpath, kind, target) = entry.split('|')
            val production = !ModuleRole.isTestClasspath(classpath)
            when (kind) {
                "project" -> {
                    if (target != path && !role.mayReach(ModuleRole.of(target), classpath)) {
                        violations += "$role may not reach $target (${ModuleRole.of(target)}) on '$classpath'"
                    }
                }

                "foreign-project" -> {
                    violations += "$role may not reach $target from another build on '$classpath'"
                }

                "module", "direct-module" -> {
                    // Only what a module declares itself: libraries may use DI internally (CameraX's
                    // camera2-pipe depends on Dagger at runtime). Konsist forbids any DI reference in our code.
                    if (production && kind == "direct-module" && ModuleRole.isDependencyInjection(target)) {
                        violations += "No DI framework or annotation library is allowed: $target on '$classpath'"
                    } else if (role.isPure && production && !ModuleRole.isAllowedInPure(target)) {
                        violations += "$role may only use kotlinx-coroutines-core, found $target on '$classpath'"
                    }
                }
            }
        }
        for (configuration in fileDependencies.get().distinct()) {
            violations += "$role may not declare files(...) dependencies, found on '$configuration'"
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Module boundary violation in $path (see docs/adr/ADR-006-module-boundaries.md):\n" +
                    violations.distinct().joinToString("\n") { "  - $it" },
            )
        }
    }
}
