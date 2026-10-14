package com.bbbjam.core.data.assistant

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * Read-only tool `jamSchedule` (D-24, `assistant-create-jam`): today, the upcoming jam and the last
 * known jam, so the agent can propose a new jam's time and venue and never a taken or past date. It
 * holds a [JamsRepository] and a [JamCalendar] and nothing else.
 */
internal class JamScheduleTool(private val jams: JamsRepository, private val calendar: JamCalendar) :
    SimpleTool<JamScheduleTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "Shows today's date, the upcoming jam (date, time, venue, status) and the last known " +
            "jam. Call it before proposing a new jam.",
    ) {

    @Serializable
    data class Args(
        @property:LLMDescription("Unused. Always an empty string.")
        val note: String = "",
    )

    override suspend fun execute(args: Args): String = render(jams.observeJams().first(), calendar.today())

    companion object {
        const val NAME = "jamSchedule"

        /** The tool's answer for [snapshot] on [today]; pure. */
        fun render(snapshot: JamsSnapshot, today: LocalDate): String {
            val upcoming = snapshot.upcoming
            val last = upcoming ?: snapshot.past.firstOrNull()
            return listOf(
                "today $today (Buenos Aires)",
                upcoming?.let { "upcoming ${line(it)} | ${statusText(it)}" } ?: "upcoming none",
                last?.let { "last ${line(it)}" } ?: "last none",
            ).joinToString("\n")
        }

        private fun line(jam: Jam) = "${jam.date} ${jam.startTime} | ${jam.venue}"

        private fun statusText(jam: Jam) = when (jam.status) {
            JamStatus.DRAFT -> "BORRADOR"
            JamStatus.CREATED -> "CREADA"
            JamStatus.PUBLISHED -> "PUBLICADA"
        }
    }
}
