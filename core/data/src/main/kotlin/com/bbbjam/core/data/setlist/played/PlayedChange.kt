package com.bbbjam.core.data.setlist.played

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * One call of [SetlistPlayedRepository.setSongPlayed] that is still sending or has not worked. [id] is unique in
 * the process and shares its counter with the other mutations, so [SetlistPlayedRepository.dismissPlayedChange]
 * takes only its own. [title] is the displayed title when the call was made. [played] is the value asked for,
 * not yet confirmed: only a [State.Sending] entry may be drawn in place of the cached value, and a failure is
 * the revert.
 */
data class PlayedChange(
    val id: Long,
    val jamDate: LocalDate,
    val songId: SongId,
    val title: String,
    val played: Boolean,
    val state: State,
) {
    sealed interface State {
        /** The write is queued or in flight. */
        data object Sending : State

        /** The mark was not changed; [reason] is never [WriteOutcome.Done]. */
        data class Failed(val reason: WriteOutcome) : State

        /** The deployed script does not know `setSongPlayed`; retrying is pointless. */
        data object Outdated : State
    }
}
