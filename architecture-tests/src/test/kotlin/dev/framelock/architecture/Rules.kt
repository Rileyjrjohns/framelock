package dev.framelock.architecture

/**
 * The architecture rules that Gradle cannot check, as functions from source files to violations (ADR-006, ADR-014).
 * Which module may see which is enforced by `verifyModuleBoundaries`: a class outside a module's reach is not on its
 * classpath, so referencing it does not compile. These rules look at what a module does with what it can see.
 *
 * Each rule selects its own scope from all the files it is given: [LayeringRulesTest] runs them on the repository,
 * [RuleFixturesTest] on fixture trees that break them, so a rule or its scope that stops matching fails its own test.
 */
internal object Rules {
    const val ROOT_PACKAGE = "dev.framelock"

    /** Every module, for the non-empty scope check. */
    val MODULES =
        listOf(
            "shared/kernel",
            "shared/testing",
            "features/live/domain",
            "features/live/application",
            "features/live/capture-uvc",
            "features/live/capture-camerax",
            "features/live/inference-litert",
            "features/live/system",
            "features/live/ui",
            "features/models/domain",
            "features/models/application",
            "features/models/store",
            "features/models/ui",
            "features/telemetry/domain",
            "features/telemetry/application",
            "features/telemetry/room",
            "app",
        )

    /** Ports named in docs/architecture.md: wherever they are declared, it must be in a port package. */
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

    /** `dev.framelock.kernel.port` or `dev.framelock.<feature>.domain.port` (ADR-014). */
    private val PORT_PACKAGE = Regex("""^dev\.framelock\.(kernel|[a-z]+\.domain)\.port$""")

    val ANDROID_PREFIXES = listOf("android.", "androidx.", "com.google.android.")
    val DI_PREFIXES = listOf("javax.inject.", "jakarta.inject.", "dagger.", "org.koin.", "com.google.inject.")

    /**
     * Imports allowed in pure modules besides Kotlin, kotlinx.coroutines and the project's own packages (ADR-006).
     * Entries ending with a dot are package prefixes; others are exact class names. Widen only through ADR-006.
     */
    val JDK_ALLOWLIST: Map<String, List<String>> =
        mapOf(
            "features/live/domain" to listOf("java.nio.ByteBuffer"),
            "features/models/domain" to listOf("java.io.InputStream"),
            "shared/testing" to listOf("java.util.concurrent."),
        )
    private val ALWAYS_ALLOWED = listOf("kotlin.", "kotlinx.coroutines.", "$ROOT_PACKAGE.")

    fun pureReferencesToAndroid(files: List<SourceFile>): List<String> =
        forbiddenReferences(files.filter { it.isPure && it.isProduction }, ANDROID_PREFIXES)

    fun referencesToDependencyInjection(files: List<SourceFile>): List<String> =
        forbiddenReferences(files.filter { it.module != null }, DI_PREFIXES)

    fun pureImportsOutsideAllowlist(files: List<SourceFile>): List<String> =
        files.filter { it.isPure && it.isProduction }.flatMap { file ->
            val allowed = ALWAYS_ALLOWED + JDK_ALLOWLIST[file.module].orEmpty()
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

    /** ADR-002: the inference thread comes from the engine adapter, never from `limitedParallelism`. */
    fun pureCallsToLimitedParallelism(files: List<SourceFile>): List<String> =
        files.filter { it.isPure && it.isProduction }.flatMap { file ->
            val called = Regex("""\blimitedParallelism\s*\(""").containsMatchIn(file.code)
            val imported = file.imports.any { it.name.endsWith(".limitedParallelism") }
            if (called || imported) listOf("$file uses limitedParallelism") else emptyList()
        }

    /**
     * Ports are the non-sealed interfaces of a port package (sealed ones, such as `SourceEvent`, are values that
     * travel through ports). They may be implemented only in adapters, in `:shared:testing` and in test sources: not
     * in a domain, an application, a UI module or the app. Checks named, nested and local types (with indirect
     * parents) and object expressions; ports may not be `fun interface`, so no SAM lambda can implement one.
     */
    fun portViolations(files: List<SourceFile>): List<String> {
        val index = TypeIndex(files)
        val ports =
            index.types
                .filter { it.isInterface && !it.isSealed && PORT_PACKAGE.matches(it.fqn.substringBeforeLast('.')) }
                .map { it.fqn }
                .toSet()
        val violations = mutableListOf<String>()

        index.types
            .filter { it.isInterface && it.simpleName in KNOWN_PORTS && it.file.isPure && it.file.isProduction }
            .filterNot { PORT_PACKAGE.matches(it.fqn.substringBeforeLast('.')) }
            .forEach { violations += "Port ${it.fqn} must be declared in a port package (${it.file})" }

        index.types
            .filter { it.fqn in ports && it.isFunInterface }
            .forEach { violations += "Port ${it.fqn} must not be a fun interface (${it.file})" }

        val restricted =
            files.filter { it.isProduction && ((it.isPure && it.module != "shared/testing") || it.isUiOrApp) }
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

    private fun forbiddenReferences(
        files: List<SourceFile>,
        prefixes: List<String>,
    ): List<String> = files.flatMap { file -> file.referencesTo(prefixes).map { "$file references $it" } }

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
