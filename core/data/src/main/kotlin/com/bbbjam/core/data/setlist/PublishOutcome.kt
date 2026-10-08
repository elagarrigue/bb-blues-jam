package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome

/** The publish result; success is returned only after the server confirms its write/read-back. */
sealed interface PublishOutcome {
    data class Published(val alreadyPublished: Boolean) : PublishOutcome
    data class NotPublished(val reason: WriteOutcome) : PublishOutcome
}

/** One pending or failed publication of a jam. */
data class SetlistPublish(val id: Long, val jamDate: java.time.LocalDate, val state: State) {
    sealed interface State {
        data object Sending : State
        data class Failed(val reason: WriteOutcome) : State
    }
}
