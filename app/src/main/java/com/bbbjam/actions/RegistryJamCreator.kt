package com.bbbjam.actions

import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.data.setlist.JamCreator
import java.time.LocalDate
import java.time.LocalTime

/**
 * Binds the assistant's [JamCreator] to the action contract (D-13): it dispatches
 * [SetlistAction.CreateJam] through the [SetlistActionRegistry] and returns the original outcome.
 */
class RegistryJamCreator(private val registry: SetlistActionRegistry) : JamCreator {
    override suspend fun createJam(date: LocalDate, startTime: LocalTime, venue: String): CreateJamOutcome =
        when (val result = registry.dispatch(SetlistAction.CreateJam(date, startTime, venue))) {
            is SetlistActionResult.JamCreated -> result.outcome
            else -> error("CreateJam returned ${result::class.simpleName}")
        }
}
