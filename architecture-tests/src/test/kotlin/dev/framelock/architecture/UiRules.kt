package dev.framelock.architecture

internal object UiRules {
    private const val MATERIAL = "androidx.compose.material3"
    private val materialComponents =
        setOf(
            "Button",
            "FilledButton",
            "ElevatedButton",
            "OutlinedButton",
            "TextButton",
            "IconButton",
            "Card",
            "CircularProgressIndicator",
            "LinearProgressIndicator",
            "Snackbar",
            "DropdownMenu",
            "DropdownMenuItem",
            "Checkbox",
            "Switch",
            "TextField",
        )

    fun materialImports(files: List<SourceFile>): List<String> =
        uiFiles(files).flatMap { file ->
            val imports =
                file.imports
                    .filter {
                        (it.name == MATERIAL && it.isWildcard) ||
                            it.name.removePrefix("$MATERIAL.") in materialComponents
                    }.map { it.name }
            val qualified =
                file.referencesTo(listOf("$MATERIAL.")).filter {
                    it.removePrefix("$MATERIAL.").substringBefore('.') in materialComponents
                }
            (imports + qualified).distinct().map { "$file uses $it directly" }
        }

    fun declarations(files: List<SourceFile>): List<String> =
        uiFiles(files).mapNotNull { file ->
            val names = file.publicUiDeclarations
            when {
                names.size > 1 -> "$file has multiple public UI declarations: $names"
                names.size == 1 && names.single() != file.fileName -> "$file must be named ${names.single()}.kt"
                else -> null
            }
        }

    fun fileLength(files: List<SourceFile>): List<String> =
        uiFiles(files)
            .filter { it.lineCount > 400 }
            .map { "$it has ${it.lineCount} lines (maximum 400)" }

    fun literalText(files: List<SourceFile>): List<String> =
        uiFiles(files).mapNotNull { file ->
            val imports = setOf("$MATERIAL.Text", "androidx.compose.material.Text")
            val names =
                file.imports
                    .filter { it.name in imports }
                    .map { it.alias.ifEmpty { "Text" } } + "Text"
            val alternatives = names.joinToString("|") { Regex.escape(it) }
            val call = Regex("\\b(?:$alternatives)\\s*\\(\\s*\"")
            val named = Regex("\\b(?:text|contentDescription)\\s*=\\s*\"")
            val hasLiteral = call.containsMatchIn(file.code) || named.containsMatchIn(file.code)
            if (hasLiteral) "$file contains literal visible text" else null
        }

    fun literalTokens(files: List<SourceFile>): List<String> =
        uiFiles(files).mapNotNull { file ->
            val dimensions = Regex("""\b\d+(?:\.\d+)?\.(?:dp|sp)\b""")
            val hasDimension = dimensions.findAll(file.code).any { it.value != "0.dp" }
            val colors =
                Regex(
                    """\bColor\s*(?:\(\s*0[xX][0-9a-fA-F]+|""" +
                        """\.(?:White|Black|Transparent|Red|Green|Blue|Gray|Yellow)\b)""",
                )
            val hasToken = hasDimension || colors.containsMatchIn(file.code)
            if (hasToken) "$file contains a literal dimension or colour" else null
        }

    private fun uiFiles(files: List<SourceFile>) = files.filter { it.isFeatureUi && it.isProduction }
}
