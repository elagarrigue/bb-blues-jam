package com.bbbjam.core.data.setlist.jamedit

import com.bbbjam.core.data.admin.WriteOutcome

/** The result of deleting a future jam (`admin-edit-delete-jam`, D-28). */
sealed interface DeleteJamOutcome {
    /** The server deleted the jam's row and tab; [songs] is how many songs its setlist had. */
    data class Deleted(val songs: Int) : DeleteJamOutcome

    /** Nothing was deleted; [reason] is the write outcome (`Rejected("setlist_changed")`, `Offline`...). */
    data class NotDeleted(val reason: WriteOutcome) : DeleteJamOutcome

    /** The deployed script does not know `deleteJam` yet: the user has to update it (migration guide). */
    data object ScriptOutdated : DeleteJamOutcome
}
