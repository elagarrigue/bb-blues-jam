package com.bbbjam.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Amber stays reserved for its five uses (`DESIGN.md`), and no Material baseline color leaks. */
class BluesJamColorsTest {

    private val palette = colorProperties(BluesJamPalette)
    private val roles = colorProperties(BluesJamColors)
    private val scheme = colorProperties(BluesJamMaterial.colorScheme)

    @Test
    fun `amber is reachable only through its five semantic roles`() {
        assertEquals(
            setOf("primaryAction", "slotOpen", "key", "published", "activeFilter"),
            roles.filterValues { it == BluesJamPalette.Amber }.keys,
        )
        assertEquals(
            setOf("onPrimaryAction", "onPublished", "onActiveFilter"),
            roles.filterValues { it == BluesJamPalette.OnAmber }.keys,
        )
        assertEquals("color roles found by reflection", 17, roles.size)
    }

    @Test
    fun `every Material color role comes from a token`() {
        assertEquals("palette values found by reflection", 11, palette.size)
        assertTrue("only ${scheme.size} ColorScheme roles found by reflection", scheme.size >= 30)
        val foreign = scheme.filterValues { it !in palette.values }
        assertTrue("ColorScheme roles not taken from a token: $foreign", foreign.isEmpty())
    }

    @Test
    fun `amber Material roles are only the primary ones`() {
        assertEquals(
            setOf("primary", "primaryContainer", "inversePrimary"),
            scheme.filterValues { it == BluesJamPalette.Amber }.keys,
        )
    }

    /**
     * Reads every Compose [Color] property of [instance] by reflection. `Color` is a value class, so
     * its getter compiles to `long get<Name>-<hash>()`; the name before the `-` is the property.
     */
    private fun colorProperties(instance: Any): Map<String, Color> = instance.javaClass.methods
        .filter { it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }
        .filter { it.name.startsWith("get") && '-' in it.name }
        .associate { method ->
            val name = method.name.removePrefix("get").substringBefore('-').replaceFirstChar { it.lowercase() }
            name to Color((method.invoke(instance) as Long).toULong())
        }
}
