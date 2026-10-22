package dev.framelock.buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `:core:*`: pure Kotlin on the JVM. The only library is kotlinx-coroutines-core (exposed as `api`,
 * because the ports expose `Flow`). detekt runs here only; see docs/adr/ADR-008-static-analysis.md.
 */
class JvmCoreConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("java-library")
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            extensions.configure<JavaPluginExtension> {
                toolchain.languageVersion.set(JavaLanguageVersion.of(libs.int("jdk")))
            }
            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
            }
            dependencies {
                "api"(platform(libs.library("kotlinx-coroutines-bom")))
                "api"(libs.library("kotlinx-coroutines-core"))
            }
            configureJvmTests()
        }
}
