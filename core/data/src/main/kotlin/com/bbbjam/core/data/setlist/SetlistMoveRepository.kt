package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Reordering contract, one of the contracts [SetlistRepository] unites. */
interface SetlistMoveRepository {
    /** Moves [songId] to an absolute 1-based position in the upcoming setlist. */
    suspend fun moveSong(jamDate: LocalDate, songId: SongId, toPosition: Int): MoveSongOutcome

    /** Moves in progress or failed, in call order. */
    fun observeMoves(): Flow<List<SetlistMove>>

    /** Removes a failed move [id]; a sending entry, an unknown id or an id of another mutation is left alone. */
    fun dismissMove(id: Long)
}
