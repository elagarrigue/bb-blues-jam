package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome

sealed interface SetSlotCountOutcome {
    data object SlotCountSet : SetSlotCountOutcome
    data class NotSet(val reason: WriteOutcome) : SetSlotCountOutcome
}
