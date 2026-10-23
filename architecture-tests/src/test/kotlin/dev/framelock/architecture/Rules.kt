package dev.framelock.architecture

/**
 * The architecture rules, as functions from source files to violations. [LayeringRulesTest] runs them on the
 * repository; [RuleFixturesTest] runs them on small fixture trees to prove each one can fail.
 */
internal object Rules {
    const val ROOT_PACKAGE = "dev.framelock"

    /** Ports live in this package (docs/architecture.md, "Ports"). */
    const val PORT_PACKAGE = "$ROOT_PACKAGE.core.domain.port"

    val CORE_MODULES = listOf("core/domain", "core/application", "core/testing")
    val ADAPTER_MODULES =
        listOf("capture-uvc", "capture-camerax", "inference-litert", "model-store", "system", "telemetry")

    /** Driven ports listed in docs/architecture.md. */
    val KNOWN_PORTS =
        listOf(
            "FrameSource",
            "InferenceEngine",
            "ModelRepository",
            "ThermalMonitor",
            "MemoryPressureMonitor",
            "DeviceInfoProvider",
            "TelemetrySink",
            "Clock",
            "Tracer",
            "AuditLog",
        )

    val ANDROID_PREFIXES = listOf("android.", "androidx.", "com.google.android.")
    val DI_PREFIXES = listOf("javax.inject.", "jakarta.inject.", "dagger.", "org.koin.", "com.google.inject.")

    private val KOTLIN = listOf("kotlin.", "kotlinx.coroutines.")

    /**
     * Imports allowed in the main sources of each core module (ADR-006). Entries ending with a dot are package
     * prefixes; others are exact class names. Extend only through a reviewed change of ADR-006.
     */
    val CORE_IMPORT_ALLOWLIST: Map<String, List<String>> =
        mapOf(
            "core/domain" to KOTLIN +
                listOf(
                    "$ROOT_PACKAGE.core.domain.",
                    "java.io.InputStream",
                    "java.nio.ByteBuffer",
                ),
            "core/application" to KOTLIN + listOf("$ROOT_PACKAGE.core.domain.", "$ROOT_PACKAGE.core.application."),
            "core/testing" to
                KOTLIN +
                listOf("$ROOT_PACKAGE.core.domain.", "$ROOT_PACKAGE.core.testing.", "java.util.concurrent."),
        )

    private val ADAPTERS = ADAPTER_MODULES.map { "adapters/$it" }

    private fun List<SourceFile>.production(vararg modules: String) =
        filter { file -> file.isProduction && modules.any { file.isIn(it) } }

    private fun List<SourceFile>.anySourceSet(vararg modules: String) = filter { file -> modules.any { file.isIn(it) } }

    // Each rule below selects its own scope from all the files it is given, so the fixtures exercise the same
    // scoping as the repository run.

    fun coreReferencesToAndroid(files: List<SourceFile>): List<String> =
        forbiddenReferences(files.production(*CORE_MODULES.toTypedArray()), ANDROID_PREFIXES)

    fun referencesToDependencyInjection(files: List<SourceFile>): List<String> =
        forbiddenReferences(files.anySourceSet(*(CORE_MODULES + ADAPTERS + "app").toTypedArray()), DI_PREFIXES)

    fun coreImportsOutsideAllowlist(files: List<SourceFile>): List<String> =
        CORE_IMPORT_ALLOWLIST.flatMap { (module, allowed) ->
            importsOutsideAllowlist(files.production(module), allowed)
        }

    fun domainReferencesToApplication(files: List<SourceFile>): List<String> =
        forbiddenReferences(files.production("core/domain"), listOf("$ROOT_PACKAGE.core.application."))

    fun adapterReferencesToOtherAdapters(files: List<SourceFile>): List<String> =
        ADAPTER_MODULES.flatMap { adapter ->
            val others =
                ADAPTER_MODULES.filter { it != adapter }.map {
                    "$ROOT_PACKAGE.adapters.${it.replace(
                        '-',
                        '.',
                    )}."
                }
            forbiddenReferences(files.anySourceSet("adapters/$adapter"), others)
        }

    fun adapterReferencesToApplication(files: List<SourceFile>): List<String> =
        forbiddenReferences(files.production(*ADAPTERS.toTypedArray()), listOf("$ROOT_PACKAGE.core.application."))

    /** ADR-002: the inference thread comes from the engine adapter, never from `limitedParallelism`. */
    fun coreCallsToLimitedParallelism(files: List<SourceFile>): List<String> =
        files.production(*CORE_MODULES.toTypedArray()).flatMap { file ->
            val called = Regex("""\blimitedParallelism\s*\(""").containsMatchIn(file.code)
            val imported = file.imports.any { it.name.endsWith(".limitedParallelism") }
            if (called || imported) listOf("$file uses limitedParallelism") else emptyList()
        }

    private fun forbiddenReferences(
        files: List<SourceFile>,
        prefixes: List<String>,
    ): List<String> = files.flatMap { file -> file.referencesTo(prefixes).map { "$file references $it" } }

    private fun importsOutsideAllowlist(
        files: List<SourceFile>,
        allowed: List<String>,
    ): List<String> =
        files.flatMap { file ->
            file.imports
                .filterNot { import ->
                    allowed.any { entry ->
                        if (entry.endsWith('.')) {
                            import.name.startsWith(entry)
                        } else {
                            import.name == entry &&
                                !import.isWildcard
                        }
                    }
                }.map { "$file imports ${it.name}${if (it.isWildcard) ".*" else ""}" }
        }

    /**
     * Ports are the non-sealed interfaces declared in [PORT_PACKAGE] (sealed ones, such as `SourceEvent`, are
     * value types that travel through ports). They may be implemented only in adapters, in `:core:testing` and in
     * test sources: not in the core, not in the app. Checks named, nested and local types (with indirect parents)
     * and object expressions; ports may not be `fun interface`, so no SAM lambda can implement one.
     */
    fun portViolations(files: List<SourceFile>): List<String> {
        val index = TypeIndex(files)
        val ports =
            index.types
                .filter { it.isInterface && !it.isSealed && it.fqn.startsWith("$PORT_PACKAGE.") }
                .map { it.fqn }
                .toSet()
        val violations = mutableListOf<String>()

        index.types
            .filter {
                it.isInterface && it.simpleName in KNOWN_PORTS && it.file.isProduction &&
                    it.file.isIn("core/domain")
            }.filterNot { it.fqn.startsWith("$PORT_PACKAGE.") }
            .forEach { violations += "Port ${it.fqn} must be declared in $PORT_PACKAGE (${it.file})" }

        index.types
            .filter { it.fqn in ports && it.isFunInterface }
            .forEach { violations += "Port ${it.fqn} must not be a fun interface (${it.file})" }

        val restricted =
            files.filter {
                it.isProduction &&
                    (it.isIn("core/domain") || it.isIn("core/application") || it.isIn("app"))
            }
        index.types
            .filter { it.file in restricted && it.fqn !in ports }
            .forEach { type ->
                val implemented = index.supertypesOf(type) intersect ports
                implemented.forEach { violations += "${type.fqn} implements port $it outside adapters (${type.file})" }
            }
        restricted.forEach { file ->
            OBJECT_EXPRESSION.findAll(file.code).forEach { match ->
                supertypeNames(match.groupValues[1])
                    .map { index.resolve(it, file) }
                    .filter { it in ports }
                    .forEach { violations += "Object expression implements port $it outside adapters ($file)" }
            }
        }
        return violations
    }

    /** `object : A, B(x), C<T> {`: the supertype list of an object expression, up to its body. */
    private val OBJECT_EXPRESSION = Regex("""\bobject\s*:([^{]+)\{""")

    private fun supertypeNames(list: String): List<String> {
        var flat = list
        while (true) {
            val next = flat.replace(Regex("""\([^()]*\)|<[^<>]*>"""), "")
            if (next == flat) break
            flat = next
        }
        return flat.split(',').mapNotNull { Regex("""[\w.]+""").find(it)?.value }
    }
}
