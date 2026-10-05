package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.jams.JamsRepository
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The user's direction of 5 October 2026 ("para las jams pasadas no importa quién tocó, solo la
 * lista de temas") by construction: no detail model has a field for who played, a filter, an admin
 * control or a row event, and the presenter cannot reach the admin flag.
 */
class PastJamDetailModelShapeTest {
    private val forbidden =
        Regex("lineup|instrument|slot|musician|extra|filter|admin|edit|event", RegexOption.IGNORE_CASE)

    private fun fields(type: Class<*>) = type.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        .map { it.name }
        .toSet()

    @Test
    fun `a past song row declares exactly position, title, artist and key`() {
        assertEquals(
            setOf("position", "positionLabel", "title", "artist", "key", "keyDescription"),
            fields(PastSongRowUiModel::class.java),
        )
    }

    @Test
    fun `no detail model has a lineup, filter, admin or event field`() {
        listOf(
            PastJamDetailUiModel.Loading::class.java,
            PastJamDetailUiModel.NotFound::class.java,
            PastJamDetailUiModel.Jam::class.java,
            PastJamHeaderUiModel::class.java,
            PastSetlistUiModel.Songs::class.java,
            PastSetlistUiModel.NotShown::class.java,
            PastSongRowUiModel::class.java,
        ).forEach { type ->
            val names = fields(type)
            assertTrue("forbidden field in ${type.simpleName}: $names", names.none { forbidden.containsMatchIn(it) })
        }
    }

    @Test
    fun `the presenter takes only the jams repository`() {
        val constructors = PastJamDetailPresenter::class.java.constructors
        assertEquals(1, constructors.size)
        assertEquals(listOf(JamsRepository::class.java), constructors.single().parameterTypes.toList())
    }
}
