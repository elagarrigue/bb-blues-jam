package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * One call of [SetlistRepository.removeSong] that is still sending or has failed. [id] is unique in
 * the process and shares its counter with [SetlistAdd.id], so [SetlistRepository.dismiss] takes
 * either. [title] is the displayed title when the call was made: the catalog's, else the tab's copy,
 * else the id.
 */
data class SetlistRemove(
    val id: Long,
    val jamDate: LocalDate,
    val songId: SongId,
    val title: String,
    val state: State,
) {
    sealed interface State {
        /** The write is queued or in flight. */
        data object Sending : State

        /** The removal did not happen; [reason] is never [WriteOutcome.Done]. */
        data class Failed(val reason: WriteOutcome) : State
    }
}
