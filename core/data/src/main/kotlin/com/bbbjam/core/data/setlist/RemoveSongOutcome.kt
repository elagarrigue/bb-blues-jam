package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome

/** What [SetlistRepository.removeSong] did. */
sealed interface RemoveSongOutcome {
    /** The Sheet no longer holds the song; later songs moved up one position there and in the cache. */
    data object Removed : RemoveSongOutcome

    /**
     * The server did not remove the song. [reason] is never [WriteOutcome.Done]. The cache is left as
     * it was, except for `Rejected("song_not_in_setlist")`: the server says the Sheet no longer has
     * the song, so the cached row is removed too.
     */
    data class NotRemoved(val reason: WriteOutcome) : RemoveSongOutcome
}
