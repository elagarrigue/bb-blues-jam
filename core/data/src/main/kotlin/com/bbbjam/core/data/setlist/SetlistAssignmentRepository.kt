package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The slot-assignment mutation (D-13) with its observable entries and dismissal. */
interface SetlistAssignmentRepository {
    /** Assigns [musicianName] to one fixed, 1-based instrument slot; the server authorizes it. */
    suspend fun assignSlot(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        musicianName: MusicianName,
    ): AssignSlotOutcome

    /** Pending and failed assignments, in call order. */
    fun observeAssignments(): Flow<List<Assignment>>

    /** Removes a failed assignment [id]; a sending entry, an unknown id or an id of another mutation is left alone. */
    fun dismissAssignment(id: Long)
}
