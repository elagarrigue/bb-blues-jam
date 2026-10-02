package com.bbbjam.core.data.jams

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.model.Jam

/**
 * The cached jams as a reader sees them on [JamCalendar.today]: [upcoming] is the earliest jam that
 * is not historical, or null; later future jams are held back (user approval P5). [past] holds the
 * historical jams, newest first, and an available past setlist keeps only filled slots, because an
 * empty cell in a past jam means "not recorded", never "open" (P6). Titles and artists come from the
 * catalog when the song is in it, from the tab's copy otherwise.
 */
data class JamsSnapshot(val upcoming: Jam?, val past: List<Jam>, val freshness: Freshness)
