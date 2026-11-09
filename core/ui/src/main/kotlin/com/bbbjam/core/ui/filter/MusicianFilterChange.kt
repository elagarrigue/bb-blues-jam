package com.bbbjam.core.ui.filter

/**
 * A change to the chosen musician, as the user asked for it (handlers carry the intent, like
 * [InstrumentFilterChange]). [Clear] removes the musician only; [ClearAll] also empties the
 * instrument selection, for the empty block whose result the instruments contributed to.
 */
sealed interface MusicianFilterChange {
    data class Select(val name: String) : MusicianFilterChange

    data object Clear : MusicianFilterChange

    data object ClearAll : MusicianFilterChange
}
