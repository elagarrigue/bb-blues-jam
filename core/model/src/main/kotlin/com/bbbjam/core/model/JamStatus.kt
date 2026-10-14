package com.bbbjam.core.model

/**
 * Stored status of a [Jam] (D-26). There is no archived value: a jam is historical when its date
 * has passed ([Jam.isHistorical]).
 */
enum class JamStatus {
    /** The admin is preparing the jam; for musicians it does not exist (no date, no list). */
    DRAFT,

    /** Musicians see the date, time and venue and that the list is being assembled, not the list. */
    CREATED,

    /** The setlist is visible to musicians. */
    PUBLISHED,
}
