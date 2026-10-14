package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.JamStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Changing the upcoming jam's status in any direction (`admin-set-jam-status`, D-26, D-13). */
interface SetlistJamStatusRepository {
    /**
     * Sets the `estado` of [jamDate] through the admin POST path. Room changes only when the server
     * echoes the status it wrote and read back; there is no optimistic status. Queued in call order
     * with the other mutations; cancelling the caller never cancels the write.
     */
    suspend fun setJamStatus(jamDate: LocalDate, status: JamStatus): SetJamStatusOutcome

    /** The status changes in progress or failed, in call order. In memory only. */
    fun observeJamStatusChanges(): Flow<List<JamStatusChange>>

    /** Removes a failed or outdated status change; a sending entry remains visible. */
    fun dismissJamStatusChange(id: Long)
}
