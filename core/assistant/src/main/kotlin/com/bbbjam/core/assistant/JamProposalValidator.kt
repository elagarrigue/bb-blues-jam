package com.bbbjam.core.assistant

import com.bbbjam.core.data.jams.isValidTime
import com.bbbjam.core.data.jams.normalizeVenue
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** A jam the agent proposes to create, already checked with the server's rules. Not yet created. */
data class ProposedJam(val date: LocalDate, val startTime: LocalTime, val venue: String)

/** Why the model's jam was not usable. */
enum class JamRejection { INVALID_DATE, PAST_DATE, INVALID_TIME, INVALID_VENUE }

/** The outcome of [JamProposalValidator.validate]. */
internal sealed interface JamValidation {
    data class Valid(val jam: ProposedJam) : JamValidation
    data class Rejected(val reason: JamRejection) : JamValidation
}

/**
 * Checks the model's [JamProposalDto] with the same rules as Apps Script's `createJam` (calendar date,
 * not before today, `HH:MM`, plain venue of 1..80 characters). Pure. The admin still confirms the
 * date in the dialog; this only keeps garbage from being drawn.
 */
internal object JamProposalValidator {
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    fun validate(dto: JamProposalDto, today: LocalDate): JamValidation {
        val date = parseDate(dto.date.trim()) ?: return JamValidation.Rejected(JamRejection.INVALID_DATE)
        val time = dto.time.trim()
        val venue = normalizeVenue(dto.venue)
        return when {
            date.isBefore(today) -> JamValidation.Rejected(JamRejection.PAST_DATE)
            !isValidTime(time) -> JamValidation.Rejected(JamRejection.INVALID_TIME)
            venue == null -> JamValidation.Rejected(JamRejection.INVALID_VENUE)
            else -> JamValidation.Valid(ProposedJam(date, LocalTime.parse(time), venue))
        }
    }

    private fun parseDate(text: String): LocalDate? = if (DATE.matches(text)) {
        try {
            LocalDate.parse(text)
        } catch (ignored: DateTimeParseException) {
            null
        }
    } else {
        null
    }
}
