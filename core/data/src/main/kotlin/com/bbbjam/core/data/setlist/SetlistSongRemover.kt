package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * The setlist removal the assistant feature may call (`assistant-proposal-targets`, D-13, D-24): take
 * a song out of a jam's setlist by id. Like [SetlistSongAdder] it has no implementation in `:core:data`
 * and no `dataModule` binding; `:app` binds it to the action registry. The outcome is
 * [SetlistRepository.removeSong]'s, unchanged.
 */
fun interface SetlistSongRemover {
    suspend fun removeSong(jamDate: LocalDate, songId: SongId): RemoveSongOutcome
}
