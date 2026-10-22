package dev.framelock.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Konsist reads the project's source files from disk, not from the classpath. Those files are declared
 * as test inputs, otherwise Gradle would report the tests UP-TO-DATE after a source change.
 */
class JvmArchitectureTestsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<JavaPluginExtension> {
                toolchain.languageVersion.set(JavaLanguageVersion.of(libs.int("jdk")))
            }
            configureJvmTests()
            dependencies {
                "testImplementation"(libs.library("konsist"))
            }

            val sources =
                isolated.rootProject.projectDirectory.asFileTree.matching {
                    include("**/src/**/*.kt")
                    exclude("**/build/**", "build-logic/**")
                }
            tasks.withType<Test>().configureEach {
                inputs
                    .files(sources)
                    .withPropertyName("projectSources")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            }
        }
}
