package com.bbbjam.core.data.setlist.lineup

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The lineup-size mutation (`admin-adjust-lineup`, D-13) with its observable entries and dismissal. */
interface SetlistLineupRepository {
    /** Changes only open slot counts, bounded by the default lineup; the server authorizes it. */
    suspend fun setSlotCount(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        count: Int,
    ): SetSlotCountOutcome

    /** Pending and failed lineup writes, sharing the mutation id counter. */
    fun observeLineupChanges(): Flow<List<LineupChange>>

    /** Removes a failed lineup change [id]; a sending entry, an unknown id or another kind's id stays. */
    fun dismissLineupChange(id: Long)
}
