package dev.framelock.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Runs `verifyModuleBoundaries` on throwaway builds whose modules use the real project paths (ADR-014).
 * Plain `java-library` modules stand in for Kotlin and Android ones: the role comes from the path only.
 */
class ModuleBoundariesPluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `valid dependency graph passes`() {
        writeBuild(
            LIVE_DOMAIN to "api(project(\"$KERNEL\"))\nimplementation(\"org.jetbrains:annotations:26.0.2\")",
            LIVE_APPLICATION to "api(project(\"$LIVE_DOMAIN\"))",
            LIVE_A to "api(project(\"$LIVE_DOMAIN\"))",
            LIVE_UI to "implementation(project(\"$LIVE_APPLICATION\"))",
            MODELS_DOMAIN to "api(project(\"$LIVE_DOMAIN\"))",
            MODELS_APPLICATION to "api(project(\"$MODELS_DOMAIN\"))\napi(project(\"$LIVE_APPLICATION\"))",
            TESTING to "api(project(\"$LIVE_DOMAIN\"))\napi(project(\"$MODELS_DOMAIN\"))",
            ":app" to
                """
                implementation(project("$MODELS_APPLICATION"))
                implementation(project("$LIVE_A"))
                implementation(project("$LIVE_UI"))
                testImplementation(project("$TESTING"))
                """,
        )

        assertSuccess()
    }

    @Test
    fun `adapter depending on another adapter fails`() {
        writeBuild(LIVE_A to "implementation(project(\"$LIVE_B\"))")

        assertViolation("ADAPTER(live) may not reach $LIVE_B")
    }

    @Test
    fun `adapter depending on an application fails`() {
        writeBuild(LIVE_A to "implementation(project(\"$LIVE_APPLICATION\"))")

        assertViolation("ADAPTER(live) may not reach $LIVE_APPLICATION")
    }

    @Test
    fun `domain depending on its application fails`() {
        writeBuild(LIVE_DOMAIN to "implementation(project(\"$LIVE_APPLICATION\"))")

        assertViolation("DOMAIN(live) may not reach $LIVE_APPLICATION")
    }

    @Test
    fun `application depending on an adapter fails`() {
        writeBuild(LIVE_APPLICATION to "implementation(project(\"$LIVE_A\"))")

        assertViolation("APPLICATION(live) may not reach $LIVE_A")
    }

    @Test
    fun `kernel depending on any project fails`() {
        writeBuild(KERNEL to "implementation(project(\"$LIVE_DOMAIN\"))")

        assertViolation("KERNEL may not reach $LIVE_DOMAIN")
    }

    @Test
    fun `upstream feature depending on a downstream feature fails`() {
        writeBuild(LIVE_DOMAIN to "implementation(project(\"$MODELS_DOMAIN\"))")

        assertViolation("DOMAIN(live) may not reach $MODELS_DOMAIN (DOMAIN(models))")
    }

    @Test
    fun `downstream feature may depend on an upstream feature`() {
        writeBuild(
            MODELS_DOMAIN to "api(project(\"$LIVE_DOMAIN\"))",
            MODELS_APPLICATION to "api(project(\"$LIVE_APPLICATION\"))",
            LIVE_APPLICATION to "api(project(\"$LIVE_DOMAIN\"))",
        )

        assertSuccess()
    }

    @Test
    fun `module of an unknown feature fails`() {
        writeBuild(extraModules = listOf(":features:payments:domain"))

        val result = run(expectFailure = true)

        assertTrue(result.output.contains("unknown feature 'payments'"), result.output)
    }

    @Test
    fun `depending on app fails`() {
        writeBuild(LIVE_A to "testImplementation(project(\":app\"))")

        assertViolation("ADAPTER(live) may not reach :app")
    }

    @Test
    fun `main configuration depending on testing fails`() {
        writeBuild(LIVE_A to "implementation(project(\"$TESTING\"))")

        assertViolation("ADAPTER(live) may not reach $TESTING")
    }

    @Test
    fun `test configuration may depend on testing`() {
        writeBuild(
            TESTING to "api(project(\"$LIVE_DOMAIN\"))\napi(project(\"$MODELS_DOMAIN\"))",
            MODELS_DOMAIN to "api(project(\"$LIVE_DOMAIN\"))",
            LIVE_APPLICATION to "testImplementation(project(\"$TESTING\"))",
            LIVE_A to "testImplementation(project(\"$TESTING\"))",
        )

        assertSuccess()
    }

    @Test
    fun `dependency substitution to another adapter fails`() {
        writeBuild(
            LIVE_A to
                """
                implementation("org.example:extra:1.0")
                configurations.configureEach {
                    resolutionStrategy.dependencySubstitution {
                        substitute(module("org.example:extra")).using(project("$LIVE_B"))
                    }
                }
                """,
        )

        assertViolation("ADAPTER(live) may not reach $LIVE_B (ADAPTER(live)) on 'compileClasspath'")
    }

    @Test
    fun `custom configuration extended by main cannot carry testing`() {
        writeBuild(
            TESTING to "api(project(\"$KERNEL\"))",
            LIVE_APPLICATION to
                """
                val sharedTestSupport = configurations.create("sharedTestSupport")
                configurations.named("implementation") { extendsFrom(sharedTestSupport) }
                sharedTestSupport(project("$TESTING"))
                """,
        )

        assertViolation("APPLICATION(live) may not reach $TESTING (TESTING) on 'compileClasspath'")
    }

    @Test
    fun `production classpath whose name contains Test cannot carry testing`() {
        writeBuild(
            TESTING to "api(project(\"$KERNEL\"))",
            ":app" to
                """
                val abTest = configurations.create("abTestImplementation")
                configurations.create("abTestReleaseRuntimeClasspath") { extendsFrom(abTest) }
                abTest(project("$TESTING"))
                """,
        )

        assertViolation("APP may not reach $TESTING (TESTING) on 'abTestReleaseRuntimeClasspath'")
    }

    @Test
    fun `test fixtures of a domain cannot carry testing to an adapter`() {
        writeBuild(
            TESTING to "api(project(\"$KERNEL\"))",
            LIVE_DOMAIN to "testFixturesApi(project(\"$TESTING\"))",
            LIVE_A to "implementation(testFixtures(project(\"$LIVE_DOMAIN\")))",
            plugins = mapOf(LIVE_DOMAIN to "`java-test-fixtures`"),
        )

        assertViolation("DOMAIN(live) may not reach $TESTING (TESTING) on 'testFixturesCompileClasspath'")
        assertViolation("ADAPTER(live) may not reach $TESTING (TESTING) on 'compileClasspath'")
    }

    @Test
    fun `transitive edge is checked`() {
        writeBuild(
            TESTING to "api(project(\"$KERNEL\"))",
            LIVE_B to "api(project(\"$TESTING\"))",
            ":app" to "implementation(project(\"$LIVE_B\"))",
        )

        assertViolation("APP may not reach $TESTING (TESTING) on 'compileClasspath'")
    }

    @Test
    fun `files dependency in a pure module fails`() {
        writeBuild(LIVE_DOMAIN to "compileOnly(files(\"libs/annotations.jar\"))")

        assertViolation("DOMAIN(live) may not declare files(...) dependencies, found on 'compileOnly'")
    }

    @Test
    fun `DI library in an adapter fails`() {
        writeBuild(LIVE_A to "implementation(\"javax.inject:javax.inject:1\")")

        assertViolation(
            "No DI framework or annotation library is allowed: javax.inject:javax.inject on 'compileClasspath'",
        )
    }

    @Test
    fun `DI library brought transitively by a third-party library is allowed outside pure modules`() {
        writeBuild(LIVE_A to "implementation(\"org.example:uses-dagger:1.0\")")

        assertSuccess()
    }

    @Test
    fun `DI library brought transitively is refused in a pure module`() {
        writeBuild(LIVE_DOMAIN to "implementation(\"org.example:uses-dagger:1.0\")")

        assertViolation("found com.google.dagger:dagger on 'compileClasspath'")
    }

    @Test
    fun `pure module with an extra external library fails`() {
        writeBuild(LIVE_APPLICATION to "implementation(\"org.example:extra:1.0\")")

        assertViolation("found org.example:extra on 'compileClasspath'")
    }

    @Test
    fun `pure module with a runtime only external library fails`() {
        writeBuild(LIVE_DOMAIN to "runtimeOnly(\"org.example:extra:1.0\")")

        assertViolation("found org.example:extra on 'runtimeClasspath'")
    }

    @Test
    fun `compileOnly DI annotation in a pure module fails`() {
        writeBuild(KERNEL to "compileOnly(\"javax.inject:javax.inject:1\")")

        assertViolation("javax.inject:javax.inject on 'compileClasspath'")
    }

    @Test
    fun `module without a role fails`() {
        writeBuild(extraModules = listOf(":feature"))

        val result = run(expectFailure = true)

        assertTrue(result.output.contains("Module :feature has no role"), result.output)
    }

    private fun assertSuccess() {
        val result = run(expectFailure = false)
        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
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
                  ${dependencyXml("$group:$artifact")}
                </project>
                """.trimIndent(),
            )
        }
    }

    private fun dependencyXml(module: String): String {
        val (group, artifact, version) = LOCAL_DEPENDENCIES[module]?.split(':') ?: return ""
        return "<dependencies><dependency><groupId>$group</groupId><artifactId>$artifact</artifactId>" +
            "<version>$version</version></dependency></dependencies>"
    }

    private companion object {
        const val KERNEL = ":shared:kernel"
        const val TESTING = ":shared:testing"
        const val LIVE_DOMAIN = ":features:live:domain"
        const val LIVE_APPLICATION = ":features:live:application"
        const val LIVE_A = ":features:live:a"
        const val LIVE_B = ":features:live:b"
        const val LIVE_UI = ":features:live:ui"
        const val MODELS_DOMAIN = ":features:models:domain"
        const val MODELS_APPLICATION = ":features:models:application"

        val MODULES =
            listOf(
                KERNEL,
                TESTING,
                LIVE_DOMAIN,
                LIVE_APPLICATION,
                LIVE_A,
                LIVE_B,
                LIVE_UI,
                MODELS_DOMAIN,
                MODELS_APPLICATION,
                ":app",
                ":architecture-tests",
            )
        val LOCAL_MODULES =
            listOf(
                Triple("org.example", "extra", "1.0"),
                Triple("javax.inject", "javax.inject", "1"),
                Triple("org.jetbrains", "annotations", "26.0.2"),
                Triple("com.google.dagger", "dagger", "2.59"),
                Triple("org.example", "uses-dagger", "1.0"),
            )

        /** Dependencies of the local POM-only modules, as `group:artifact:version`. */
        val LOCAL_DEPENDENCIES = mapOf("org.example:uses-dagger" to "com.google.dagger:dagger:2.59")
    }
}
