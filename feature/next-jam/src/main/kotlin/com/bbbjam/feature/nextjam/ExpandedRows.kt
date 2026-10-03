package com.bbbjam.feature.nextjam

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import java.time.LocalDate

/**
 * Which rows of the upcoming jam are expanded: local presentation state, not data (no mutation, so
 * nothing for D-13). Keyed by the Sheet's `posicion` ([positions]), which is never renumbered, so a
 * refresh that fills a name or drops another row keeps the right row open. Keyed by call order or
 * list index instead, a dropped row would hand its expansion to a neighbour.
 *
 * [jamDate] scopes the positions to one jam: a new upcoming jam reads as all collapsed without
 * recreating the state. Stale positions are harmless and never pruned.
 */
internal data class ExpandedRows(val jamDate: LocalDate?, val positions: Set<Int>) {
    fun isExpanded(date: LocalDate, position: Int): Boolean = date == jamDate && position in positions

    /** Flips one row and leaves every other row as it was; another jam's date starts afresh. */
    fun toggle(date: LocalDate, position: Int): ExpandedRows = when {
        date != jamDate -> ExpandedRows(date, setOf(position))
        position in positions -> copy(positions = positions - position)
        else -> copy(positions = positions + position)
    }

    companion object {
        val NONE = ExpandedRows(jamDate = null, positions = emptySet())

        /** `[date or null, ArrayList<Int>]`: both bundle-safe, so expansion survives rotation. */
        val Saver: Saver<ExpandedRows, Any> = listSaver(
            save = { listOf(it.jamDate?.toString(), ArrayList(it.positions)) },
            restore = { saved ->
                val date = (saved[0] as String?)?.let(LocalDate::parse)
                ExpandedRows(date, (saved[1] as List<*>).map { it as Int }.toSet())
            },
        )
    }
}
