package com.bbbjam.feature.catalog

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.catalog.CatalogSnapshot
import com.bbbjam.core.data.catalog.edit.CatalogEditRepository
import com.bbbjam.core.data.catalog.edit.CreateSongOutcome
import com.bbbjam.core.data.catalog.edit.DeleteSongOutcome
import com.bbbjam.core.data.catalog.edit.SongFields
import com.bbbjam.core.data.catalog.edit.UpdateSongOutcome
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred

/** Records every catalog write and answers the outcomes the test sets; a [hold] keeps a call open. */
internal class FakeCatalogEdits : CatalogEditRepository {
    val creates = mutableListOf<SongFields>()
    val updates = mutableListOf<Pair<SongId, SongFields>>()
    val deletes = mutableListOf<SongId>()
    var hold: CompletableDeferred<Unit>? = null
    var create: CreateSongOutcome = CreateSongOutcome.Created(SongId("new-song"))
    var update: UpdateSongOutcome = UpdateSongOutcome.Updated(changed = true)
    var delete: DeleteSongOutcome = DeleteSongOutcome.Deleted

    override suspend fun createSong(fields: SongFields): CreateSongOutcome {
        creates += fields
        hold?.await()
        return create
    }

    override suspend fun updateSong(id: SongId, fields: SongFields): UpdateSongOutcome {
        updates += id to fields
        hold?.await()
        return update
    }

    override suspend fun deleteSong(id: SongId): DeleteSongOutcome {
        deletes += id
        hold?.await()
        return delete
    }
}
