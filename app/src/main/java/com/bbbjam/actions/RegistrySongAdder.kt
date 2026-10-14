package com.bbbjam.actions

import com.bbbjam.core.data.setlist.AddSongOutcome
import com.bbbjam.core.data.setlist.SetlistSongAdder
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * Binds the assistant's [SetlistSongAdder] to the action contract (D-13): it dispatches
 * [SetlistAction.AddSong] through the [SetlistActionRegistry] and returns the original outcome.
 */
class RegistrySongAdder(private val registry: SetlistActionRegistry) : SetlistSongAdder {
    override suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome =
        when (val result = registry.dispatch(SetlistAction.AddSong(jamDate, songId, key))) {
            is SetlistActionResult.SongAdded -> result.outcome
            else -> error("AddSong returned ${result::class.simpleName}")
        }
}
