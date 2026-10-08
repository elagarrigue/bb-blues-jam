package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Reordering contract, implemented alongside the rest of [SetlistRepository]. */
interface SetlistMoveRepository {
    /** Moves [songId] to an absolute 1-based position in the upcoming setlist. */
    suspend fun moveSong(jamDate: LocalDate, songId: SongId, toPosition: Int): MoveSongOutcome

    /** Moves in progress or failed, in call order. */
    fun observeMoves(): Flow<List<SetlistMove>>
}
