package com.bbbjam.core.model

/**
 * One instrument position in a [Lineup]. Open when [musicianName] is null, filled otherwise; there
 * is no intermediate state and no Musician entity (D-05). A blank name is rejected so that
 * open/filled never depends on whitespace; a mapper maps an empty cell to null.
 */
data class Slot(val instrument: Instrument, val musicianName: String? = null) {
    init {
        require(musicianName == null || musicianName.isNotBlank()) {
            "Blank musician name \"$musicianName\" on a $instrument slot: use null for an open slot"
        }
    }

    /** True when nobody is assigned: the slot is a place to play. */
    val isOpen: Boolean
        get() = musicianName == null

    /** True when a musician is assigned. */
    val isFilled: Boolean
        get() = !isOpen
}
