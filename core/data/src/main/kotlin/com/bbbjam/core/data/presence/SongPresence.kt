package com.bbbjam.core.data.presence

import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * Advisory presence of an admin on one song detail (`admin-presence-notice`, D-32). Not a mutation:
 * it changes no entity, is never persisted, locks nothing and authorizes nothing. The server keeps
 * it in its cache for 120 seconds and the passphrase guard still protects it.
 */
interface SongPresence {
    /** Says "this install is here" and answers whether another install is too. Never throws for I/O. */
    suspend fun announce(jamDate: LocalDate, songId: SongId): PresenceAnswer

    /** Best-effort release, fire-and-forget; it runs in the data scope, so it survives the caller. */
    fun leave(jamDate: LocalDate, songId: SongId)
}
