package com.bbbjam.core.data.presence

/** What an announce learned (D-32). Only a count of others ever crosses the wire, never who they are. */
sealed interface PresenceAnswer {
    /** Nobody else is on the song. */
    data object Alone : PresenceAnswer

    /** At least one other admin announced the same song within the server's expiry. */
    data object OthersHere : PresenceAnswer

    /** Offline, refused, an old script or a malformed answer: the caller keeps what it knew. */
    data object Unknown : PresenceAnswer
}
