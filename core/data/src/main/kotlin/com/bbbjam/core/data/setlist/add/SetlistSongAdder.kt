package com.bbbjam.core.data.setlist.add

import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * The one setlist mutation the assistant feature may call (`assistant-apply-proposal`, D-13, D-24):
 * add a catalog song to a future jam's setlist (the upcoming one or a held-back one, D-29) with the key
 * given. It has no implementation in `:core:data` and no `dataModule` binding; `:app` binds it to the
 * setlist repository, so the assistant reaches the setlist only through this narrow contract and
 * cannot reach publish or clear. The outcome is [SetlistRepository.addSong]'s,
 * unchanged.
 */
fun interface SetlistSongAdder {
    suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome
}
