package com.bbbjam.core.data.musician

import kotlinx.coroutines.flow.Flow

/**
 * The musician the next-jam list is filtered by, remembered on this device (`musician-filter`). A
 * local preference, not a Sheet or setlist mutation: nothing is sent anywhere, and it never
 * authorizes anything. The stored text is the name as chosen; matching is left to the caller
 * (`musicianMatchKey`).
 */
interface MusicianFilterPreference {
    /** The chosen name, or null when none; emits the stored value first, then every change. */
    fun observeSelected(): Flow<String?>

    /** Remembers [name]; a null or blank name clears it. */
    suspend fun select(name: String?)
}
