package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome
import java.time.LocalDate
import java.time.LocalTime

/** The result of creating the next jam (`assistant-create-jam`, D-25). */
sealed interface CreateJamOutcome {
    /** The server appended the `BORRADOR` row and echoed it; [venue] is the server-normalized text. */
    data class Created(val date: LocalDate, val startTime: LocalTime, val venue: String) : CreateJamOutcome

    /** Nothing was created; [reason] is the write outcome (`Rejected("jam_exists")`, `AccessRefused`...). */
    data class NotCreated(val reason: WriteOutcome) : CreateJamOutcome

    /** The deployed script does not know `createJam` yet: the user has to update it (migration guide). */
    data object ScriptOutdated : CreateJamOutcome
}
