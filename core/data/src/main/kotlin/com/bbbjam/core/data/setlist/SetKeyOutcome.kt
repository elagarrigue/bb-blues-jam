package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome

/** What [SetlistRepository.setKey] did. */
sealed interface SetKeyOutcome {
    /** The Sheet holds the new key in that song's `tono` cell, and so does the cache. */
    data object KeySet : SetKeyOutcome

    /**
     * The key was not changed, in the Sheet or in the cache. [reason] is never [WriteOutcome.Done].
     * `Rejected("song_not_in_setlist")` also leaves the cache alone: the next refresh brings the
     * Sheet's setlist.
     */
    data class NotSet(val reason: WriteOutcome) : SetKeyOutcome
}
