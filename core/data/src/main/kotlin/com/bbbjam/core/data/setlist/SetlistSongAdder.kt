package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * The one setlist mutation the assistant feature may call (`assistant-apply-proposal`, D-13, D-24):
 * add a catalog song to a jam's setlist with the key given. It has no implementation in `:core:data`
 * and no `dataModule` binding; `:app` binds it to the action registry, so the assistant reaches the
 * setlist only through the same contract every mutation is registered in, and cannot reach remove,
 * publish or clear. The outcome is [SetlistRepository.addSong]'s, unchanged.
 */
fun interface SetlistSongAdder {
    suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome
}
