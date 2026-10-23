package dev.framelock.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue

/** Source files of the repository, and of the rule fixtures. */
internal object Scopes {
    private const val FIXTURES = "architecture-tests/src/test/fixtures"

    /**
     * Every Kotlin file of the repository, except build-logic, this module (its fixtures break rules on purpose) and
     * hidden directories at the root (tool caches, local worktrees). A hidden directory inside a module is checked.
     */
    val project: List<SourceFile> by lazy {
        val root = Konsist.projectRootPath.replace('\\', '/').trimEnd('/')
        Konsist
            .scopeFromProject()
            .files
            .map(::SourceFile)
            .filter { isProjectSource(it.path.removePrefix(root)) }
    }

    /** [relativePath] starts with `/`, relative to the repository root. */
    fun isProjectSource(relativePath: String): Boolean =
        !relativePath.startsWith("/build-logic/") &&
            !relativePath.startsWith("/architecture-tests/") &&
            !ROOT_HIDDEN.containsMatchIn(relativePath)

    private val ROOT_HIDDEN = Regex("^/\\.[^/]+/")

    /** Production sources of a module given by its directory, e.g. `features/live/domain`. Fails if empty. */
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
