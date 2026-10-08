package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Public contract for the admin-only compare-and-clear mutation and its in-flight/failure state. */
interface SetlistSlotClearRepository {
    /** Clears one occupied active slot if the server still sees the expected musician there. */
    suspend fun clearSlot(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        expectedName: String,
    ): ClearSlotOutcome

    /** Pending and failed clears, in call order. */
    fun observeSlotClears(): Flow<List<SlotClear>>
}
