package com.bbbjam.actions

import com.bbbjam.core.data.setlist.RemoveSongOutcome
import com.bbbjam.core.data.setlist.SetlistSongRemover
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * Binds the assistant's [SetlistSongRemover] to the action contract (D-13): it dispatches
 * [SetlistAction.RemoveSong] through the [SetlistActionRegistry] and returns the original outcome.
 */
class RegistrySongRemover(private val registry: SetlistActionRegistry) : SetlistSongRemover {
    override suspend fun removeSong(jamDate: LocalDate, songId: SongId): RemoveSongOutcome =
        when (val result = registry.dispatch(SetlistAction.RemoveSong(jamDate, songId))) {
            is SetlistActionResult.SongRemoved -> result.outcome
            else -> error("RemoveSong returned ${result::class.simpleName}")
        }
}
