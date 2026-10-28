package dev.framelock.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.combined.KoClassAndInterfaceAndObjectDeclaration

/**
 * A Kotlin source file as the rules see it: where it lives (module, source set) and what it references.
 * References are read from the code with comments and string literals removed, so a fully qualified name
 * used without an import is seen, and a name mentioned in a comment is not.
 */
internal class SourceFile(
    private val declaration: KoFileDeclaration,
) {
    val path: String = declaration.path.replace('\\', '/')

    /** `main`, `debug`, `test`, `androidTest`, `testFixtures`, ... */
    private val sourceSet: String =
        SOURCE_SET
            .findAll(path)
            .lastOrNull()
            ?.groupValues
            ?.get(1)
            .orEmpty()

    /** Everything except unit and instrumented test sources. Test fixtures count as production: others consume them. */
    val isProduction: Boolean =
        sourceSet.isNotEmpty() &&
            !sourceSet.startsWith("androidTest") &&
            !(sourceSet.startsWith("test") && !sourceSet.startsWith("testFixtures"))

    /** Module directory, e.g. `features/live/domain`, `shared/kernel` or `app`; null outside any module. */
    val module: String? =
        MODULE
            .findAll(path)
            .lastOrNull()
            ?.groupValues
            ?.get(1)

    /** The shared modules and every feature's `domain` and `application`: pure Kotlin (ADR-014). */
    val isPure: Boolean =
        module != null && (module in setOf("shared/kernel", "shared/testing") || PURE_FEATURE_LAYER.matches(module))

    /** A feature's Compose module, or the app: they may not implement ports. */
    val isUiOrApp: Boolean = module in setOf("app", "shared/designsystem") || module?.endsWith("/ui") == true

    val code: String by lazy { stripCommentsAndStrings(declaration.text) }

    val packageName: String by lazy {
        PACKAGE
            .find(code)
            ?.groupValues
            ?.get(1)
            .orEmpty()
    }

    val imports: List<Import> by lazy {
        IMPORT
            .findAll(code)
            .map {
                Import(
                    name = it.groupValues[1],
                    isWildcard = it.groupValues[2].isNotEmpty(),
                    alias = it.groupValues[4],
                )
            }.toList()
    }

    val types: List<KoClassAndInterfaceAndObjectDeclaration> by lazy {
        declaration.classesAndInterfacesAndObjects(includeNested = true, includeLocal = true)
    }

    /** True if the file belongs to the module in [moduleDir], e.g. `core/domain` or `adapters/system`. */
    fun isIn(moduleDir: String): Boolean =
        Regex("/${Regex.escape(moduleDir)}/src/[^/]+/(kotlin|java)/").containsMatchIn(path)

    /** Imports or fully qualified references starting with one of [prefixes] (each ending with a dot). */
    fun referencesTo(prefixes: List<String>): List<String> {
        val imported = imports.map { it.name }.filter { name -> prefixes.any { name.startsWith(it) } }
        val qualified =
            QUALIFIED_NAME
                .findAll(code.replace(IMPORT, "").replace(PACKAGE, ""))
                .map { it.value }
                .filter { name -> prefixes.any { name.startsWith(it) } }
        return (imported + qualified).distinct()
    }

    override fun toString(): String = path

    data class Import(
        val name: String,
        val isWildcard: Boolean,
        val alias: String,
    )

    companion object {
        /** The last `/src/<set>/kotlin/` (or `java`) segment: the repository may itself sit under a `src` directory. */
        private val SOURCE_SET = Regex("""/src/([^/]+)/(kotlin|java)/""")
        private val MODULE =
            Regex("""/(shared/[a-z0-9-]+|features/[a-z0-9-]+/[a-z0-9-]+|app)/src/[^/]+/(?:kotlin|java)/""")
        private val PURE_FEATURE_LAYER = Regex("features/[a-z0-9-]+/(domain|application)")
        private val PACKAGE = Regex("""(?m)^\s*package\s+([\w.]+)""")
        private val IMPORT = Regex("""(?m)^\s*import\s+([\w.]+?)(\.\*)?(\s+as\s+(\w+))?\s*;?\s*$""")
        private val QUALIFIED_NAME = Regex("""(?<![\w.])[a-z]\w*(\.\w+)+""")

        fun isInterface(type: KoClassAndInterfaceAndObjectDeclaration): Boolean = type is KoInterfaceDeclaration

        /** Removes comments, string and char literals; keeps line structure. */
        fun stripCommentsAndStrings(text: String): String {
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val rest = text.length - i
                when {
                    text.startsWith("//", i) -> {
                        while (i < text.length && text[i] != '\n') i++
                    }

                    text.startsWith("/*", i) -> {
                        val end = text.indexOf("*/", i + 2)
                        i = if (end < 0) text.length else end + 2
                        out.append(' ')
                    }

                    rest >= 3 && text.startsWith("\"\"\"", i) -> {
                        val end = text.indexOf("\"\"\"", i + 3)
                        i = if (end < 0) text.length else end + 3
                        out.append("\"\"")
                    }

                    text[i] == '"' || text[i] == '\'' -> {
                        val quote = text[i]
                        i++
                        while (i < text.length && text[i] != quote && text[i] != '\n') {
                            if (text[i] == '\\') i++
                            i++
                        }
                        i++
                        out.append(quote).append(quote)
                    }

                    else -> {
                        out.append(text[i])
                        i++
                    }
                }
            }
            return out.toString()
        }
    }
}
