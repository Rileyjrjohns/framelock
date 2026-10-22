package dev.framelock.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.jupiter.api.Assertions.assertTrue

/** Source files of the repository, grouped the way the architecture rules talk about them. */
internal object Scopes {
    const val ROOT_PACKAGE = "dev.framelock"
    val CORE_MODULES = listOf("core/domain", "core/application", "core/testing")
    val ADAPTER_MODULES =
        listOf("capture-uvc", "capture-camerax", "inference-litert", "model-store", "system", "telemetry")

    private val allFiles: List<KoFileDeclaration> by lazy {
        Konsist.scopeFromProject().files.filterNot { it.normalizedPath.contains("/build-logic/") }
    }

    /** Main (production) sources of a module given by its directory, e.g. `core/domain`. */
    fun mainFiles(moduleDir: String): List<KoFileDeclaration> =
        allFiles.filter { it.normalizedPath.contains("/$moduleDir/src/main/") }.requireNotEmpty(moduleDir)

    /** Every source set of a module: main, test, androidTest. */
    fun allFiles(moduleDir: String): List<KoFileDeclaration> =
        allFiles.filter { it.normalizedPath.contains("/$moduleDir/src/") }.requireNotEmpty(moduleDir)

    fun adapterPackage(adapter: String): String = "$ROOT_PACKAGE.adapters." + adapter.replace('-', '.')

    /** A rule over an empty scope would pass without checking anything. */
    private fun List<KoFileDeclaration>.requireNotEmpty(moduleDir: String): List<KoFileDeclaration> {
        assertTrue(isNotEmpty(), "No Kotlin source found for $moduleDir: the rule would check nothing")
        return this
    }

    private val KoFileDeclaration.normalizedPath: String
        get() = path.replace('\\', '/')
}
