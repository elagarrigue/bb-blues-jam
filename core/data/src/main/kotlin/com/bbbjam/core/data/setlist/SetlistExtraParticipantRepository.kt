package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface SetlistExtraParticipantRepository {
    suspend fun addExtraParticipant(
        jamDate: LocalDate,
        songId: SongId,
        name: String,
        instrument: String,
    ): ExtraParticipantOutcome
    suspend fun removeExtraParticipant(
        jamDate: LocalDate,
        songId: SongId,
        ordinal: Int,
        expectedName: String,
        expectedInstrument: String,
    ): ExtraParticipantOutcome
    fun observeExtraParticipantChanges(): Flow<List<ExtraParticipantChange>>

    /** Removes a failed extra-participant change [id]; a sending entry, unknown id or other kind's id stays. */
    fun dismissExtraParticipantChange(id: Long)
}
