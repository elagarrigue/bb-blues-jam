package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * The setlist reorder the assistant feature may call (`assistant-proposal-targets`, D-13, D-24): move a
 * song to an absolute 1-based position. Like [SetlistSongAdder] it has no implementation in `:core:data`
 * and no `dataModule` binding; `:app` binds it to the action registry. The outcome is
 * [SetlistRepository.moveSong]'s, unchanged.
 */
fun interface SetlistSongMover {
    suspend fun moveSong(jamDate: LocalDate, songId: SongId, toPosition: Int): MoveSongOutcome
}
