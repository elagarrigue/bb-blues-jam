package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome

sealed interface AssignSlotOutcome {
    data object Assigned : AssignSlotOutcome
    data class NotAssigned(val reason: WriteOutcome) : AssignSlotOutcome
}
