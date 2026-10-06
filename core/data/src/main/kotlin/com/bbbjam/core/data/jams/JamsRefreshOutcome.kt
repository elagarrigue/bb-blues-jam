package com.bbbjam.core.data.jams

import com.bbbjam.core.data.DataFailure
import java.time.LocalDate

/** The result of [JamsRepository.refresh]. */
sealed interface JamsRefreshOutcome {
    /**
     * The cache now holds [jamCount] jams. [rejected] rows were left out; [issues] are about kept
     * jams' setlists (dropped rows, ignored draft setlists, script errors); [heldBack] are future
     * jams after the upcoming one, not shown (user approval P5). None of the lists is persisted.
     * [adminRead] is true when the jams came from the admin read, drafts' songs included
     * (`admin-add-song-to-setlist`).
     */
    data class Updated(
        val jamCount: Int,
        val rejected: List<RejectedJam>,
        val issues: List<SetlistIssue>,
        val heldBack: List<LocalDate>,
        val adminRead: Boolean = false,
    ) : JamsRefreshOutcome

    /** Nothing was stored; the cached jams are untouched. [adminRead] as in [Updated]. */
    data class Failed(val failure: DataFailure, val adminRead: Boolean = false) : JamsRefreshOutcome
}

/**
 * One log line for the outcome: counts, dates, row indexes, issue names and the failure kind, ending
 * in ` (admin read)` after an admin read. It never holds the URL, the passphrase or a musician's
 * name.
 */
fun JamsRefreshOutcome.toLogLine(): String = when (this) {
    is JamsRefreshOutcome.Updated -> buildString {
        append(
            "jams refresh: updated $jamCount jams, ${rejected.size} rejected, ${issues.size} setlist issues, " +
                "${heldBack.size} held back",
        )
        rejected.forEach { append("; rejected #${it.index} ${it.date ?: "(no date)"}: ${it.issues.joinToString()}") }
        issues.forEach { append("; ").append(it.describe()) }
        heldBack.forEach { append("; held back $it") }
        if (adminRead) append(ADMIN_READ)
    }

    is JamsRefreshOutcome.Failed -> "jams refresh: failed $failure" + if (adminRead) ADMIN_READ else ""
}

private const val ADMIN_READ = " (admin read)"

private fun SetlistIssue.describe(): String = when (this) {
    is SetlistIssue.DraftSetlistIgnored -> "$date: draft setlist ignored"

    is SetlistIssue.SetlistMissing -> "$date: no setlist and no error"

    is SetlistIssue.SetlistError -> "$date: setlistError $code"

    is SetlistIssue.RowIssues ->
        "$date row #$index ${if (dropped) "dropped" else "kept"}: ${issues.joinToString()}"
}
