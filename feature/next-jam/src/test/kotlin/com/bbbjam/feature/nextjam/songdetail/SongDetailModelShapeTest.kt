package com.bbbjam.feature.nextjam.songdetail

import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.feature.nextjam.overlay.SongOverlaySources
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-20 by construction: the detail model has no field for the catalog's optional data, and the
 * presenter cannot reach the catalog at all (its admin part, `song-detail-replaces-row-expansion`,
 * adds only the admin flag and the setlist writes).
 */
class SongDetailModelShapeTest {
    private val forbidden = Regex("tempo|tag|difficulty|songsterr|artwork", RegexOption.IGNORE_CASE)

    @Test
    fun `the Song model declares exactly the approved fields`() {
        val fields = SongDetailUiModel.Song::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .map { it.name }
            .toSet()
        assertEquals(
            setOf("title", "artist", "keyLabel", "key", "keyDescription", "lineup", "back", "admin"),
            fields,
        )
        assertTrue("forbidden field in $fields", fields.none { forbidden.containsMatchIn(it) })
    }

    @Test
    fun `the presenter takes the jams, the admin flag and the song-level setlist contracts, never the catalog`() {
        val constructors = SongDetailPresenter::class.java.constructors
        assertEquals(1, constructors.size)
        assertEquals(
            listOf(JamsRepository::class.java, AdminSession::class.java, SongOverlaySources::class.java),
            constructors.single().parameterTypes.toList(),
        )
    }
}
