package dev.framelock.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalStateException("No library '$alias' in libs.versions.toml") }

internal fun VersionCatalog.int(alias: String): Int =
    findVersion(alias)
        .orElseThrow { IllegalStateException("No version '$alias' in libs.versions.toml") }
        .requiredVersion
        .toInt()

/** JUnit Jupiter + coroutines-test on the local (JVM) test classpath; see docs/adr/ADR-007-test-frameworks.md. */
internal fun Project.configureJvmTests() {
    dependencies {
        "testImplementation"(platform(libs.library("junit-bom")))
        "testImplementation"(libs.library("junit-jupiter"))
        "testImplementation"(platform(libs.library("kotlinx-coroutines-bom")))
        "testImplementation"(libs.library("kotlinx-coroutines-test"))
        "testRuntimeOnly"(libs.library("junit-platform-launcher"))
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
