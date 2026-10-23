package dev.framelock.architecture

/**
 * Types declared in a set of source files, with their supertypes resolved the way Kotlin resolves a type name:
 * fully qualified, explicit import (or alias), same package, then star imports.
 */
internal class TypeIndex(
    files: List<SourceFile>,
) {
    class Type(
        val fqn: String,
        val simpleName: String,
        val file: SourceFile,
        val isInterface: Boolean,
        val isSealed: Boolean,
        val isFunInterface: Boolean,
        /** `enum class` or `value class`: a closed or wrapped value, never free text by itself. */
        val isEnumOrValue: Boolean,
        /** Primary constructor properties as (name, type as written). */
        val properties: List<Pair<String, String>>,
        val parentNames: List<String>,
    )

    val types: List<Type> =
        files.flatMap { file ->
            file.types.map { type ->
                val header = SourceFile.stripCommentsAndStrings(type.text).substringBefore('{')
                Type(
                    fqn =
                        type.fullyQualifiedName.takeUnless { it.isNullOrEmpty() } ?: "${file.packageName}.${type.name}",
                    simpleName = type.name,
                    file = file,
                    isInterface = SourceFile.isInterface(type),
                    isSealed = Regex("""\bsealed\s+interface\b""").containsMatchIn(header),
                    isFunInterface = Regex("""\bfun\s+interface\b""").containsMatchIn(header),
                    isEnumOrValue = Regex("""\b(enum|value)\s+class\b""").containsMatchIn(header),
                    properties = PROPERTY.findAll(header).map { it.groupValues[1] to it.groupValues[2] }.toList(),
                    parentNames =
                        type.parents(indirectParents = false).mapNotNull { parent ->
                            TYPE_NAME.find(SourceFile.stripCommentsAndStrings(parent.text))?.value?.trim()
                        },
                )
            }
        }

    private val byFqn: Map<String, Type> = types.associateBy { it.fqn }

    /** Resolves [name] as written in [file] to a fully qualified name; returns [name] unchanged if unknown. */
    fun resolve(
        name: String,
        file: SourceFile,
    ): String {
        val first = name.substringBefore('.')
        val rest = name.removePrefix(first)
        file.imports
            .firstOrNull {
                !it.isWildcard && (
                    it.alias == first || (
                        it.alias.isEmpty() &&
                            it.name.endsWith(
                                ".$first",
                            )
                    )
                )
            }?.let { return it.name + rest }
        if (name.contains('.') && name in byFqn) return name
        "${file.packageName}.$name".let { if (it in byFqn) return it }
        file.imports
            .filter { it.isWildcard }
            .map { "${it.name}.$name" }
            .firstOrNull { it in byFqn }
            ?.let { return it }
        return name
    }

    /** All supertypes of [type], direct and indirect, as fully qualified names when they are known. */
    fun supertypesOf(type: Type): Set<String> {
        val result = mutableSetOf<String>()
        val pending = ArrayDeque(listOf(type))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            for (parent in current.parentNames.map { resolve(it, current.file) }) {
                if (result.add(parent)) byFqn[parent]?.let { pending += it }
            }
        }
        return result
    }

    private companion object {
        val TYPE_NAME = Regex("""^\s*[\w.]+""")
        val PROPERTY = Regex("""\bval\s+(\w+)\s*:\s*([\w.]+\??)""")
    }
}
