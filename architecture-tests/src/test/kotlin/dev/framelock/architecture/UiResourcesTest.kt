package dev.framelock.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class UiResourcesTest {
    private val modules = listOf("app", "features/live/ui", "features/models/ui", "shared/designsystem")
    private val placeholder = Regex("""%(?:\d+\$)?[.\d]*[sdf]""")

    @Test
    fun `every UI key names its feature screen and element`() {
        modules.forEach { module ->
            val feature = if (module.startsWith("features/")) module.split('/')[1] else module.substringAfterLast('/')
            strings(module, "values").keys.forEach { key ->
                assertTrue(key.matches(Regex("${feature}_[a-z]+_[a-z0-9_]+")), "$module: $key")
            }
        }
    }

    @Test
    fun `French translations cover every key with the same placeholders`() {
        modules.forEach { module ->
            val english = strings(module, "values")
            val french = strings(module, "values-fr")
            assertEquals(english.keys, french.keys, module)
            english.forEach { (key, value) ->
                assertTrue(french.getValue(key).isNotBlank(), "$module: $key")
                assertEquals(placeholders(value), placeholders(french.getValue(key)), "$module: $key")
            }
        }
    }

    private fun placeholders(value: String) =
        placeholder
            .findAll(value)
            .map { it.value }
            .sorted()
            .toList()

    private fun strings(
        module: String,
        directory: String,
    ): Map<String, String> {
        val path = File(Konsist.projectRootPath, "$module/src/main/res/$directory/strings.xml")
        val strings =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(
                    path,
                ).getElementsByTagName("string")
        return (0 until strings.length).associate { index ->
            val node = strings.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }
}
