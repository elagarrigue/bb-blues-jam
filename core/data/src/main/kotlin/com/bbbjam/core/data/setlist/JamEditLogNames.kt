package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.admin.WriteOutcome

/** The outcome names of the jam edit failure log lines; never a venue or a passphrase. */
internal fun WriteOutcome.logName(): String = when (this) {
    WriteOutcome.AccessRefused -> "AccessRefused"
    WriteOutcome.Offline -> "Offline"
    WriteOutcome.Unavailable, WriteOutcome.Done -> "Unavailable"
    is WriteOutcome.Rejected -> "Rejected($code)"
}

internal fun UpdateJamOutcome.logName(): String = when (this) {
    is UpdateJamOutcome.Updated -> "Updated"
    UpdateJamOutcome.ScriptOutdated -> "ScriptOutdated"
    is UpdateJamOutcome.NotUpdated -> reason.logName()
}

internal fun DeleteJamOutcome.logName(): String = when (this) {
    is DeleteJamOutcome.Deleted -> "Deleted"
    DeleteJamOutcome.ScriptOutdated -> "ScriptOutdated"
    is DeleteJamOutcome.NotDeleted -> reason.logName()
}
