package com.bbbjam.core.data.setlist

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Publication mutation and its observable sending/failure state. */
interface SetlistPublishRepository {
    /** Publishes only after Apps Script writes and reads back `PUBLICADA`; this never overlays status. */
    suspend fun publishSetlist(jamDate: LocalDate): PublishOutcome

    /** The in-flight or failed publication, retained until success or explicit dismissal. */
    fun observePublishes(): Flow<List<SetlistPublish>>

    /** Removes a failed publication; a sending entry remains visible. */
    fun dismissPublish(id: Long)
}
