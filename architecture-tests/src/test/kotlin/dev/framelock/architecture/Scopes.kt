package dev.framelock.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue

/** Source files of the repository, and of the rule fixtures. */
internal object Scopes {
    private const val FIXTURES = "architecture-tests/src/test/fixtures"

    /** Every Kotlin file of the repository, except build-logic and this module (its fixtures break rules on purpose). */
    val project: List<SourceFile> by lazy {
        Konsist
            .scopeFromProject()
            .files
            .map(::SourceFile)
            .filterNot { it.path.contains("/build-logic/") || it.path.contains("/architecture-tests/") }
    }

    /** Production sources of a module given by its directory, e.g. `core/domain`. Fails if empty. */
    fun production(moduleDir: String): List<SourceFile> =
        project.filter { it.isIn(moduleDir) && it.isProduction }.requireNotEmpty(moduleDir)

    /** Every source set of a module. Fails if empty. */
    fun all(moduleDir: String): List<SourceFile> = project.filter { it.isIn(moduleDir) }.requireNotEmpty(moduleDir)

    fun fixture(name: String): List<SourceFile> =
        Konsist
            .scopeFromDirectory("$FIXTURES/$name")
            .files
            .map(::SourceFile)
            .requireNotEmpty("fixture $name")

    /** A rule over an empty scope would pass without checking anything. */
    private fun List<SourceFile>.requireNotEmpty(what: String): List<SourceFile> {
        assertTrue(isNotEmpty(), "No Kotlin source found for $what: the rule would check nothing")
        return this
    }
}
