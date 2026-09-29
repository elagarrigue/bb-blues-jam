package com.bbbjam.core.model

/**
 * Stored status of a [Jam]. There is no archived value: a jam is historical when its date has
 * passed ([Jam.isHistorical]).
 */
enum class JamStatus {
    /** The admin is building the setlist; musicians see that it is being assembled. */
    DRAFT,

    /** The setlist is visible to musicians. */
    PUBLISHED,
}
