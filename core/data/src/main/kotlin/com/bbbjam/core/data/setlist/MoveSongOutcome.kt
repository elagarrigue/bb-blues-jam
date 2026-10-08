package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome

/** What [SetlistRepository.moveSong] did. */
sealed interface MoveSongOutcome {
    /** The Sheet accepted the new absolute position; the cache was mirrored when it was available. */
    data object Moved : MoveSongOutcome

    /** The Sheet rejected or could not confirm the move. */
    data class NotMoved(val reason: WriteOutcome) : MoveSongOutcome
}
