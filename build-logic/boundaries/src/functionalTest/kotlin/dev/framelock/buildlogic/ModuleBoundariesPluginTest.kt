package dev.framelock.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Runs `verifyModuleBoundaries` on throwaway builds whose modules use the real project paths.
 * Plain `java-library` modules stand in for Kotlin and Android ones: the role comes from the path only.
 */
class ModuleBoundariesPluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `valid dependency graph passes`() {
        writeBuild(
            ":core:application" to "implementation(project(\":core:domain\"))",
            ":core:testing" to "api(project(\":core:domain\"))",
            ":adapters:a" to "api(project(\":core:domain\"))",
            ":app" to
                """
                implementation(project(":core:application"))
                implementation(project(":adapters:a"))
                testImplementation(project(":core:testing"))
                """,
            ":core:domain" to "implementation(\"org.jetbrains:annotations:26.0.2\")",
        )

        val result = run(expectFailure = false)

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
    }

    @Test
    fun `adapter depending on another adapter fails`() {
        writeBuild(":adapters:a" to "implementation(project(\":adapters:b\"))")

        assertViolation("ADAPTER may not reach :adapters:b")
    }

    @Test
    fun `adapter depending on core application fails`() {
        writeBuild(":adapters:a" to "implementation(project(\":core:application\"))")

        assertViolation("ADAPTER may not reach :core:application")
    }

    @Test
    fun `core domain depending on any project fails`() {
        writeBuild(":core:domain" to "implementation(project(\":core:testing\"))")

        assertViolation("CORE_DOMAIN may not reach :core:testing")
    }

    @Test
    fun `core application depending on an adapter fails`() {
        writeBuild(":core:application" to "implementation(project(\":adapters:a\"))")

        assertViolation("CORE_APPLICATION may not reach :adapters:a")
    }

    @Test
    fun `depending on app fails`() {
        writeBuild(":adapters:a" to "testImplementation(project(\":app\"))")

        assertViolation("ADAPTER may not reach :app")
    }

    @Test
    fun `main configuration depending on core testing fails`() {
        writeBuild(":adapters:a" to "implementation(project(\":core:testing\"))")

        assertViolation("ADAPTER may not reach :core:testing")
    }

    @Test
    fun `test configuration may depend on core testing`() {
        writeBuild(
            ":core:testing" to "api(project(\":core:domain\"))",
            ":core:application" to "testImplementation(project(\":core:testing\"))",
            ":adapters:a" to "testImplementation(project(\":core:testing\"))",
        )

        val result = run(expectFailure = false)

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
    }

    @Test
    fun `dependency substitution to another adapter fails`() {
        writeBuild(
            ":adapters:a" to
                """
                implementation("org.example:extra:1.0")
                configurations.configureEach {
                    resolutionStrategy.dependencySubstitution {
                        substitute(module("org.example:extra")).using(project(":adapters:b"))
                    }
                }
                """,
        )

        assertViolation("ADAPTER may not reach :adapters:b (ADAPTER) on 'compileClasspath'")
    }

    @Test
    fun `custom configuration extended by main cannot carry core testing`() {
        writeBuild(
            ":core:testing" to "api(project(\":core:domain\"))",
            ":core:application" to
                """
                val sharedTestSupport = configurations.create("sharedTestSupport")
                configurations.named("implementation") { extendsFrom(sharedTestSupport) }
                sharedTestSupport(project(":core:testing"))
                """,
        )

        assertViolation("CORE_APPLICATION may not reach :core:testing (CORE_TESTING) on 'compileClasspath'")
    }

    @Test
    fun `production classpath whose name contains Test cannot carry core testing`() {
        writeBuild(
            ":core:testing" to "api(project(\":core:domain\"))",
            ":app" to
                """
                val abTest = configurations.create("abTestImplementation")
                configurations.create("abTestReleaseRuntimeClasspath") { extendsFrom(abTest) }
                abTest(project(":core:testing"))
                """,
        )

        assertViolation("APP may not reach :core:testing (CORE_TESTING) on 'abTestReleaseRuntimeClasspath'")
    }

    @Test
    fun `test fixtures of core cannot carry core testing to an adapter`() {
        writeBuild(
            ":core:testing" to "api(project(\":core:domain\"))",
            ":core:domain" to "testFixturesApi(project(\":core:testing\"))",
            ":adapters:a" to "implementation(testFixtures(project(\":core:domain\")))",
            plugins = mapOf(":core:domain" to "`java-test-fixtures`"),
        )

        assertViolation("CORE_DOMAIN may not reach :core:testing (CORE_TESTING) on 'testFixturesCompileClasspath'")
        assertViolation("ADAPTER may not reach :core:testing (CORE_TESTING) on 'compileClasspath'")
    }

    @Test
    fun `transitive edge is checked`() {
        writeBuild(
            ":core:testing" to "api(project(\":core:domain\"))",
            ":adapters:b" to "api(project(\":core:testing\"))",
            ":app" to "implementation(project(\":adapters:b\"))",
        )

        assertViolation("APP may not reach :core:testing (CORE_TESTING) on 'compileClasspath'")
    }

    @Test
    fun `files dependency in core fails`() {
        writeBuild(":core:domain" to "compileOnly(files(\"libs/annotations.jar\"))")

        assertViolation("CORE_DOMAIN may not declare files(...) dependencies, found on 'compileOnly'")
    }

    @Test
    fun `DI library in an adapter fails`() {
        writeBuild(":adapters:a" to "implementation(\"javax.inject:javax.inject:1\")")

        assertViolation(
            "No DI framework or annotation library is allowed: javax.inject:javax.inject on 'compileClasspath'",
        )
    }

    @Test
    fun `core module with an extra external library fails`() {
        writeBuild(":core:application" to "implementation(\"org.example:extra:1.0\")")

        assertViolation("found org.example:extra on 'compileClasspath'")
    }

    @Test
    fun `core module with a runtime only external library fails`() {
        writeBuild(":core:domain" to "runtimeOnly(\"org.example:extra:1.0\")")

        assertViolation("found org.example:extra on 'runtimeClasspath'")
    }

    @Test
    fun `compileOnly DI annotation in core fails`() {
        writeBuild(":core:domain" to "compileOnly(\"javax.inject:javax.inject:1\")")

        assertViolation("javax.inject:javax.inject on 'compileClasspath'")
    }

    @Test
    fun `module without a role fails`() {
        writeBuild(extraModules = listOf(":feature"))

        val result = run(expectFailure = true)

        assertTrue(result.output.contains("Module :feature has no role"), result.output)
    }

    private fun assertViolation(expected: String) {
        val result = run(expectFailure = true)
        assertTrue(result.output.contains("Module boundary violation"), result.output)
        assertTrue(result.output.contains(expected), result.output)
    }

    private fun run(expectFailure: Boolean): BuildResult {
        val runner =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("verifyModuleBoundaries", "--configuration-cache", "--stacktrace")
        return if (expectFailure) runner.buildAndFail() else runner.build()
    }

    private fun writeBuild(
        vararg dependencies: Pair<String, String>,
        extraModules: List<String> = emptyList(),
        plugins: Map<String, String> = emptyMap(),
    ) {
        val modules = MODULES + extraModules
        writeLocalRepository()
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            plugins { id("framelock.module-boundaries") }
            dependencyResolutionManagement {
                repositories { maven { url = uri("${projectDir.resolve("repo").toURI()}") } }
            }
            rootProject.name = "fixture"
            ${modules.joinToString("\n") { "include(\"$it\")" }}
            """.trimIndent(),
        )
        val byModule = dependencies.toMap()
        for (module in modules) {
            val dir = projectDir.resolve(module.removePrefix(":").replace(':', '/'))
            dir.mkdirs()
            dir.resolve("build.gradle.kts").writeText(
                """
                plugins {
                    `java-library`
                    ${plugins[module].orEmpty()}
                }
                dependencies {
                    ${byModule[module].orEmpty()}
                }
                """.trimIndent(),
            )
        }
    }

    /** POM-only modules: dependency resolution needs metadata, not jars. */
    private fun writeLocalRepository() {
        for ((group, artifact, version) in LOCAL_MODULES) {
            val dir = projectDir.resolve("repo/${group.replace('.', '/')}/$artifact/$version")
            dir.mkdirs()
            dir.resolve("$artifact-$version.pom").writeText(
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>$group</groupId>
                  <artifactId>$artifact</artifactId>
                  <version>$version</version>
                  <packaging>pom</packaging>
                </project>
                """.trimIndent(),
            )
        }
    }

    private companion object {
        val MODULES =
            listOf(
                ":core:domain",
                ":core:application",
                ":core:testing",
                ":adapters:a",
                ":adapters:b",
                ":app",
                ":architecture-tests",
            )
        val LOCAL_MODULES =
            listOf(
                Triple("org.example", "extra", "1.0"),
                Triple("javax.inject", "javax.inject", "1"),
                Triple("org.jetbrains", "annotations", "26.0.2"),
            )
    }
}
