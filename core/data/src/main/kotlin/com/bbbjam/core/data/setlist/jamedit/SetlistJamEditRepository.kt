package com.bbbjam.core.data.setlist.jamedit

import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

/** Editing and deleting a future jam (`admin-edit-delete-jam`, D-28, D-13). */
interface SetlistJamEditRepository {
    /**
     * Sets the date, time and venue of the future jam [jamDate] through the admin POST path; a new
     * date moves the jam with its tab. Room changes only when the server echoes what it wrote and read
     * back; there is no optimistic edit. Queued in call order with the other mutations; cancelling
     * the caller never cancels the write.
     */
    suspend fun updateJam(jamDate: LocalDate, newDate: LocalDate, startTime: LocalTime, venue: String): UpdateJamOutcome

    /**
     * Deletes the future jam [jamDate] with its tab. [expectedSongs] is the song count the admin
     * confirmed; the server refuses (`setlist_changed`) when the tab has another count. Room drops the
     * jam only after the server confirms.
     */
    suspend fun deleteJam(jamDate: LocalDate, expectedSongs: Int): DeleteJamOutcome

    /** The edits and deletions in progress or failed, in call order. In memory only. */
    fun observeJamEdits(): Flow<List<JamEdit>>

    /** Removes a failed or outdated edit; a sending entry remains visible. */
    fun dismissJamEdit(id: Long)
}
