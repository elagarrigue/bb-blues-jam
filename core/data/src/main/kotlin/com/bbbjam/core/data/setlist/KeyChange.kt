package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * One call of [SetlistRepository.setKey] that is still sending or has failed. [id] is unique in the
 * process and shares its counter with [SetlistAdd.id] and [SetlistRemove.id], so
 * [SetlistRepository.dismiss] takes any of them. [title] is the displayed title when the call was
 * made: the catalog's, else the tab's copy, else the id. [key] is the key asked for, not yet
 * confirmed: only a [State.Sending] entry may be drawn in place of the cached key, and a failure is
 * the revert.
 */
data class KeyChange(
    val id: Long,
    val jamDate: LocalDate,
    val songId: SongId,
    val title: String,
    val key: Key,
    val state: State,
) {
    sealed interface State {
        /** The write is queued or in flight. */
        data object Sending : State

        /** The key was not changed; [reason] is never [WriteOutcome.Done]. */
        data class Failed(val reason: WriteOutcome) : State
    }
}
