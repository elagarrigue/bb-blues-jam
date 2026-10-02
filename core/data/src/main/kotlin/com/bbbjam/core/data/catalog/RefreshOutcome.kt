package com.bbbjam.core.data.catalog

import com.bbbjam.core.data.DataFailure

/** The result of [CatalogRepository.refresh]. */
sealed interface RefreshOutcome {
    /**
     * The cache now holds [songCount] songs. [rejected] rows were left out and [dropped] songs were
     * kept without some optional fields. Neither list is persisted.
     */
    data class Updated(val songCount: Int, val rejected: List<RejectedSong>, val dropped: List<DroppedFields>) :
        RefreshOutcome

    /** Nothing was stored; the cached songs are untouched. */
    data class Failed(val failure: DataFailure) : RefreshOutcome
}

/**
 * One log line for the outcome: counts, the rows' ids and issues, and the failure kind. It never holds
 * the URL, and the response body only through an [DataFailure.InvalidResponse] detail.
 */
fun RefreshOutcome.toLogLine(): String = when (this) {
    is RefreshOutcome.Updated -> buildString {
        append(
            "catalog refresh: updated $songCount songs, ${rejected.size} rejected, ${dropped.size} with dropped fields",
        )
        rejected.forEach { append("; rejected #${it.index} ${it.id ?: "(no id)"}: ${it.issues.joinToString()}") }
        dropped.forEach { append("; dropped #${it.index} ${it.id}: ${it.issues.joinToString()}") }
    }

    is RefreshOutcome.Failed -> "catalog refresh: failed $failure"
}
