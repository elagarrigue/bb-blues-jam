package com.bbbjam.feature.songdetail

import com.bbbjam.core.data.jams.JamsRepository
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-20 by construction: the detail model has no field for the catalog's optional data, and the
 * presenter cannot reach the catalog at all.
 */
class SongDetailModelShapeTest {
    private val forbidden = Regex("tempo|tag|difficulty|songsterr|artwork", RegexOption.IGNORE_CASE)

    @Test
    fun `the Song model declares exactly the approved fields`() {
        val fields = SongDetailUiModel.Song::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .map { it.name }
            .toSet()
        assertEquals(setOf("title", "artist", "keyLabel", "key", "keyDescription", "lineup", "back"), fields)
        assertTrue("forbidden field in $fields", fields.none { forbidden.containsMatchIn(it) })
    }

    @Test
    fun `the presenter takes only the jams repository`() {
        val constructors = SongDetailPresenter::class.java.constructors
        assertEquals(1, constructors.size)
        assertEquals(listOf(JamsRepository::class.java), constructors.single().parameterTypes.toList())
    }
}
