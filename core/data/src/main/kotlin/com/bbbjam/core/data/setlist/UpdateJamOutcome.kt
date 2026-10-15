package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome
import java.time.LocalDate
import java.time.LocalTime

/** The result of editing a future jam's date, time and venue (`admin-edit-delete-jam`, D-28). */
sealed interface UpdateJamOutcome {
    /**
     * The server wrote and read back the jam. [date] is the jam's date now, [venue] the server-normalized
     * text; [changed] is false when nothing differed and nothing was written.
     */
    data class Updated(val date: LocalDate, val startTime: LocalTime, val venue: String, val changed: Boolean) :
        UpdateJamOutcome

    /** Nothing changed; [reason] is the write outcome (`Rejected("jam_exists")`, `Offline`...). */
    data class NotUpdated(val reason: WriteOutcome) : UpdateJamOutcome

    /** The deployed script does not know `updateJam` yet: the user has to update it (migration guide). */
    data object ScriptOutdated : UpdateJamOutcome
}
