package dev.framelock.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
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

/** Registers `verifyModuleBoundaries` and makes `check` depend on it. */
class ModuleBoundariesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val role = ModuleRole.of(project.path)
        val verify =
            project.tasks.register<VerifyModuleBoundaries>("verifyModuleBoundaries") {
                group = "verification"
                description = "Fails if this module depends on a module or library its role forbids."
                modulePath.set(project.path)
                projectDependencies.set(project.provider { declaredProjectDependencies(project) })
            }
        if (role.isCore) {
            project.pluginManager.withPlugin("java") {
                verify.configure {
                    for (name in listOf("compileClasspath", "runtimeClasspath")) {
                        val root =
                            project.configurations
                                .getByName(name)
                                .incoming.resolutionResult.rootComponent
                        externalModules.addAll(root.map { externalModulesOf(it).map { module -> "$name: $module" } })
                    }
                }
            }
        }
        project.plugins.withType<BasePlugin> {
            project.tasks.named("check") { dependsOn(verify) }
        }
    }

    private fun declaredProjectDependencies(project: Project): List<String> =
        project.configurations.flatMap { configuration ->
            configuration.dependencies
                .withType(ProjectDependency::class.java)
                .map { "${configuration.name}$SEPARATOR${it.path}" }
        }

    private fun externalModulesOf(root: ResolvedComponentResult): List<String> {
        val seen = mutableSetOf<ResolvedComponentResult>()
        val pending = ArrayDeque(listOf(root))
        val modules = sortedSetOf<String>()
        while (pending.isNotEmpty()) {
            val component = pending.removeFirst()
            if (!seen.add(component)) continue
            (component.id as? ModuleComponentIdentifier)?.let { modules += "${it.group}:${it.module}" }
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { pending += it.selected }
        }
        return modules.toList()
    }

    internal companion object {
        const val SEPARATOR = " -> "
    }
}

abstract class VerifyModuleBoundaries : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    /** Entries `configuration -> :project:path`. */
    @get:Input
    abstract val projectDependencies: ListProperty<String>

    /** Entries `classpath: group:module`, only for `:core:*` modules. */
    @get:Input
    abstract val externalModules: ListProperty<String>

    @TaskAction
    fun verify() {
        val path = modulePath.get()
        val role = ModuleRole.of(path)
        val violations = mutableListOf<String>()
        for (entry in projectDependencies.get()) {
            val (configuration, target) = entry.split(ModuleBoundariesPlugin.SEPARATOR)
            if (target == path) continue
            role.projectDependencyViolation(configuration, target)?.let { violations += it }
        }
        for (entry in externalModules.get()) {
            val module = entry.substringAfter(": ")
            if (!ModuleRole.isAllowedInCore(module)) {
                violations += "$role may only use kotlinx-coroutines-core, found $entry"
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Module boundary violation in $path (see docs/adr/ADR-006-module-boundaries.md):\n" +
                    violations.distinct().joinToString("\n") { "  - $it" },
            )
        }
    }
}
