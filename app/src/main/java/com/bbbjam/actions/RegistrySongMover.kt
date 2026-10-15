package com.bbbjam.actions

import com.bbbjam.core.data.setlist.MoveSongOutcome
import com.bbbjam.core.data.setlist.SetlistSongMover
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * Binds the assistant's [SetlistSongMover] to the action contract (D-13): it dispatches
 * [SetlistAction.MoveSong] through the [SetlistActionRegistry] and returns the original outcome.
 */
class RegistrySongMover(private val registry: SetlistActionRegistry) : SetlistSongMover {
    override suspend fun moveSong(jamDate: LocalDate, songId: SongId, toPosition: Int): MoveSongOutcome =
        when (val result = registry.dispatch(SetlistAction.MoveSong(jamDate, songId, toPosition))) {
            is SetlistActionResult.SongMoved -> result.outcome
            else -> error("MoveSong returned ${result::class.simpleName}")
        }
}
