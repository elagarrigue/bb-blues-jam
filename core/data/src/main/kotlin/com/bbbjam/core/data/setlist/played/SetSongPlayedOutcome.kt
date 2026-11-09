package com.bbbjam.core.data.setlist.played

import com.bbbjam.core.data.admin.WriteOutcome

/** The result of marking or unmarking a song as played (`song-played-state`, D-31). */
sealed interface SetSongPlayedOutcome {
    /** The server wrote and read back [played] (or the song already had it) and the cache mirrors it. */
    data class PlayedSet(val played: Boolean) : SetSongPlayedOutcome

    /** Nothing changed; [reason] is the write outcome (`Rejected("jam_not_editable")`, `Offline`...). */
    data class NotSet(val reason: WriteOutcome) : SetSongPlayedOutcome

    /** The deployed script does not know `setSongPlayed` yet: the user has to update it (migration guide). */
    data object ScriptOutdated : SetSongPlayedOutcome
}
