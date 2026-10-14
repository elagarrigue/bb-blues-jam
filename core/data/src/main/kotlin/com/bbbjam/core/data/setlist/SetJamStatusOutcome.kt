package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.JamStatus

/** The result of changing the upcoming jam's status (`admin-set-jam-status`, D-26). */
sealed interface SetJamStatusOutcome {
    /** The server wrote and read back [status]; [changed] is false when the jam already had it. */
    data class StatusSet(val status: JamStatus, val changed: Boolean) : SetJamStatusOutcome

    /** Nothing changed; [reason] is the write outcome (`Rejected("jam_not_editable")`, `Offline`...). */
    data class NotSet(val reason: WriteOutcome) : SetJamStatusOutcome

    /** The deployed script does not know `setJamStatus` yet: the user has to update it (migration guide). */
    data object ScriptOutdated : SetJamStatusOutcome
}
