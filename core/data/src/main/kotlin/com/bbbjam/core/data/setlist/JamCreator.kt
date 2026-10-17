package com.bbbjam.core.data.setlist

import java.time.LocalDate
import java.time.LocalTime

/**
 * The one jam mutation the assistant feature may call (`assistant-create-jam`, D-13, D-24): create
 * the next jam as a draft. Like [SetlistSongAdder] it has no implementation in `:core:data` and no
 * `dataModule` binding; `:app` binds it to the setlist repository. The outcome is
 * [SetlistJamCreationRepository.createJam]'s, unchanged.
 */
fun interface JamCreator {
    suspend fun createJam(date: LocalDate, startTime: LocalTime, venue: String): CreateJamOutcome
}
