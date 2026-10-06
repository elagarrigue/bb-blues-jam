package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * One call of [SetlistRepository.addSong] that is still sending or has failed. [id] is unique in
 * the process; [title] and [artist] are the cached catalog's (for a song not in the cache, the id
 * and an empty artist).
 */
data class SetlistAdd(
    val id: Long,
    val jamDate: LocalDate,
    val songId: SongId,
    val title: String,
    val artist: String,
    val key: Key,
    val state: State,
) {
    sealed interface State {
        /** The write is queued or in flight. */
        data object Sending : State

        /** The write did not happen; [reason] is never [WriteOutcome.Done]. */
        data class Failed(val reason: WriteOutcome) : State
    }
}
