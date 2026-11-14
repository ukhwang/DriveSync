package com.drivesync.tools.tokens

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * design/tokens.json is the single source of truth for colors, sizes and type
 * (the Figma variables use the same names). When the design changes in Figma:
 *   1. edit design/tokens.json
 *   2. run `./gradlew -Pdomain.only=true :tools:design-tokens:test`
 *   3. this test lists every value in Theme.kt that must follow
 */
class DesignTokenSyncTest {

    private val tokens: JsonObject = Json.parseToJsonElement(file("tokens.json").readText()).jsonObject
    private val theme: String = file("theme.kt").readText()

    private fun file(key: String): File {
        val path = System.getProperty(key) ?: fail("system property $key not set (run through Gradle)")
        return File(path).also { assertTrue(it.exists(), "missing $path") }
    }

    /** kebab-case token name -> PascalCase `DS` property, e.g. surface-raised -> SurfaceRaised. */
    private fun dsName(token: String) = token.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }

    private fun themeColors(): Map<String, String> =
        Regex("""val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)""").findAll(theme)
            .associate { it.groupValues[1] to "#" + it.groupValues[2].uppercase() }

    @Test
    fun `every token color matches Theme kt and Theme kt has no extra colors`() {
        val expected = tokens.getValue("color").jsonObject.mapKeys { dsName(it.key) }
            .mapValues { it.value.jsonPrimitive.content.uppercase() }
        val actual = themeColors()
        val problems = buildList {
            expected.forEach { (name, hex) ->
                when (val found = actual[name]) {
                    null -> add("DS.$name is missing (token ${hex})")
                    hex -> Unit
                    else -> add("DS.$name = $found but tokens.json says $hex")
                }
            }
            (actual.keys - expected.keys).forEach { add("DS.$it is not in tokens.json") }
        }
        assertTrue(problems.isEmpty(), "Theme.kt is out of sync with design/tokens.json:\n" + problems.joinToString("\n"))
    }

    @Test
    fun `sizes match`() {
        val mapping = mapOf(
            "Gutter" to ("space" to "gutter"),
            "Gap" to ("space" to "gap"),
            "Radius" to ("radius" to "card"),
            "TouchTarget" to ("size" to "touch-target-min"),
            "MemoButton" to ("size" to "memo-button"),
        )
        mapping.forEach { (ds, path) ->
            val expected = tokens.getValue(path.first).jsonObject.getValue(path.second).jsonPrimitive.int
            val actual = Regex("""val $ds = (\d+)\.dp""").find(theme)?.groupValues?.get(1)?.toInt()
            assertEquals(expected, actual, "DS.$ds must be ${expected}.dp (tokens.json ${path.first}.${path.second})")
        }
    }

    @Test
    fun `type scale matches`() {
        val mapping = mapOf(
            "displayLarge" to "display",
            "headlineLarge" to "headline-l",
            "headlineMedium" to "headline-m",
            "titleLarge" to "title",
            "bodyLarge" to "body-l",
            "bodyMedium" to "body-m",
            "labelLarge" to "label",
            "labelMedium" to "label-s",
        )
        val weights = mapOf("Bold" to 700, "SemiBold" to 600, "Medium" to 500, "Normal" to 400)
        mapping.forEach { (style, token) ->
            val spec = tokens.getValue("type").jsonObject.getValue(token).jsonObject
            val line = Regex("""$style = TextStyle\(([^)]*)\)""").find(theme)?.groupValues?.get(1)
                ?: fail("Theme.kt has no $style")
            val size = Regex("""fontSize = (\d+)\.sp""").find(line)?.groupValues?.get(1)?.toInt()
            val weight = Regex("""FontWeight\.(\w+)""").find(line)?.groupValues?.get(1)?.let(weights::get) ?: 400
            assertEquals(spec.getValue("size").jsonPrimitive.int, size, "$style fontSize (token type.$token)")
            assertEquals(spec.getValue("weight").jsonPrimitive.int, weight, "$style fontWeight (token type.$token)")
        }
    }
}
