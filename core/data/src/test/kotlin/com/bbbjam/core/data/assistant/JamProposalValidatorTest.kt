package com.bbbjam.core.data.assistant

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class JamProposalValidatorTest {
    private val today = LocalDate.of(2026, 10, 14)

    private fun check(date: String = "2026-11-28", time: String = "21:00", venue: String = "La Macanuda") =
        JamProposalValidator.validate(JamProposalDto(date, time, venue), today)

    private fun rejected(reason: JamRejection) = JamValidation.Rejected(reason)

    @Test
    fun `a good jam is valid and today counts`() {
        assertEquals(
            JamValidation.Valid(ProposedJam(LocalDate.of(2026, 11, 28), LocalTime.of(21, 0), "La Macanuda")),
            check(),
        )
        assertEquals(
            JamValidation.Valid(ProposedJam(today, LocalTime.of(0, 5), "Bar")),
            check("2026-10-14", "00:05", "Bar"),
        )
    }

    @Test
    fun `the date must be a real calendar day not before today`() {
        assertEquals(rejected(JamRejection.INVALID_DATE), check(date = "2026-02-30"))
        assertEquals(rejected(JamRejection.INVALID_DATE), check(date = "28/11/2026"))
        assertEquals(rejected(JamRejection.INVALID_DATE), check(date = ""))
        assertEquals(rejected(JamRejection.PAST_DATE), check(date = "2026-10-13"))
    }

    @Test
    fun `the time must be HH MM`() {
        listOf("9:00", "24:00", "21:60", "21", "").forEach {
            assertEquals(it, rejected(JamRejection.INVALID_TIME), check(time = it))
        }
    }

    @Test
    fun `the venue is normalized and plain`() {
        assertEquals(
            JamValidation.Valid(ProposedJam(LocalDate.of(2026, 11, 28), LocalTime.of(21, 0), "La Macanuda")),
            check(venue = "  La    Macanuda "),
        )
        listOf("", "   ", "x".repeat(81), "=SUM(1)", "-x", "@bar", "+1", "Bar\u0007").forEach {
            assertEquals(it, rejected(JamRejection.INVALID_VENUE), check(venue = it))
        }
        assertEquals(true, check(venue = "x".repeat(80)) is JamValidation.Valid)
    }
}
