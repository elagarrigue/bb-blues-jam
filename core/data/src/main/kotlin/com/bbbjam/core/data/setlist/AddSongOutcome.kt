package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome

/** What [SetlistRepository.addSong] did. */
sealed interface AddSongOutcome {
    /** The Sheet holds the song at [position], the server's `posicion` for it. */
    data class Added(val position: Int) : AddSongOutcome

    /**
     * Nothing was added, in the Sheet or in the cache. [reason] is never [WriteOutcome.Done]: a song
     * not in the cached catalog is `Rejected("unknown_song")` with no request sent, and an answer
     * that is `ok` but malformed is [WriteOutcome.Unavailable].
     */
    data class NotAdded(val reason: WriteOutcome) : AddSongOutcome
}
