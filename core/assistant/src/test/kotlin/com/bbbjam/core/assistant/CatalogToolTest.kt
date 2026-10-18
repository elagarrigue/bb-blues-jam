package com.bbbjam.core.assistant

import com.bbbjam.core.model.Tempo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogToolTest {
    private val songs = listOf(
        song("sweet-little-angel", "Sweet Little Angel", "B.B. King", "Bm", Tempo.SLOW),
        song("el-tren-de-las-4", "El tren de las cuatro", "Pappo", "E"),
        song("cafe-con-leche", "Café con leche", "Los Ñoños", "A", Tempo.FAST),
    )

    @Test
    fun `an empty query lists the whole catalog in sheet order with compact lines`() {
        val text = CatalogTool.render(songs, "")

        assertEquals(
            """
            Catalog: 3 songs (id | title | artist | key | tempo)
            sweet-little-angel | Sweet Little Angel | B.B. King | Bm | slow
            el-tren-de-las-4 | El tren de las cuatro | Pappo | E
            cafe-con-leche | Café con leche | Los Ñoños | A | fast
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `the filter ignores case and accents on title and artist`() {
        assertEquals(
            listOf("cafe-con-leche"),
            CatalogTool.render(songs, "CAFE").lines().drop(1).map { it.substringBefore(" | ") },
        )
        assertEquals(
            listOf("cafe-con-leche"),
            CatalogTool.render(songs, "noños").lines().drop(1).map { it.substringBefore(" | ") },
        )
        assertEquals(
            listOf("el-tren-de-las-4"),
            CatalogTool.render(songs, " pappo ").lines().drop(1).map { it.substringBefore(" | ") },
        )
    }

    @Test
    fun `a query with no match says so and lists no song`() {
        assertEquals(
            "Catalog: 0 songs match \"zzz\" (id | title | artist | key | tempo)",
            CatalogTool.render(songs, "zzz"),
        )
    }

    @Test
    fun `an empty cache says the catalog is not loaded`() {
        assertEquals(CatalogTool.NOT_LOADED, CatalogTool.render(emptyList(), ""))
    }

    @Test
    fun `the tool reads the repository and exposes only its name and one argument`() = runBlocking {
        val catalog = FakeCatalog(songs)
        val tool = CatalogTool(catalog)

        val text = tool.execute(CatalogTool.Args("angel"))

        assertTrue(text.contains("sweet-little-angel | Sweet Little Angel | B.B. King | Bm | slow"))
        assertEquals("searchCatalog", tool.name)
        assertEquals(1, catalog.subscriptions)
        assertEquals(listOf("query"), tool.descriptor.requiredParameters.map { it.name })
    }
}
