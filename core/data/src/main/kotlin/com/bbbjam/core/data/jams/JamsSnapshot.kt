package com.bbbjam.core.data.jams

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus

/**
 * The cached jams as a reader sees them on [JamCalendar.today]: [upcoming] is the earliest jam that
 * is not historical, or null; later future jams are in [heldBack], in date order, and are not shown
 * (user approval P5). [past] holds the historical jams, newest first, and an available past setlist
 * keeps only filled slots, because an empty cell in a past jam means "not recorded", never "open"
 * (P6). Titles and artists come from the catalog when the song is in it, from the tab's copy
 * otherwise.
 *
 * This is the raw view, draft jams included (an admin read caches them). Musician screens read
 * [forMusicians].
 */
data class JamsSnapshot(
    val upcoming: Jam?,
    val past: List<Jam>,
    val freshness: Freshness,
    val heldBack: List<Jam> = emptyList(),
) {
    /**
     * The musician projection (D-26): every BORRADOR jam is dropped from upcoming, held back and
     * past, then the split is redone, so a later CREADA or PUBLICADA jam becomes the upcoming one
     * when the earliest future jam is a draft. Past jams keep their order. Idempotent; keeps
     * [freshness].
     */
    fun forMusicians(): JamsSnapshot {
        val future = (listOfNotNull(upcoming) + heldBack).filter { it.status != JamStatus.DRAFT }
        return copy(
            upcoming = future.firstOrNull(),
            past = past.filter { it.status != JamStatus.DRAFT },
            heldBack = future.drop(1),
        )
    }
}
