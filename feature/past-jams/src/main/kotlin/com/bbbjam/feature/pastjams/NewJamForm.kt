package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.jams.JamHeldBack
import com.bbbjam.core.data.jams.JamPlan
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.jams.VenueProblem
import com.bbbjam.core.data.jams.isValidTime
import com.bbbjam.core.data.jams.jamCreationPlan
import com.bbbjam.core.data.jams.normalizeVenue
import com.bbbjam.core.data.jams.venueProblem
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime

/*
 * The "Nueva jam" form's rules (`admin-jams-tab`, J7/J8), pure so a JVM test can check them. The venue and
 * time rules and the creation plan are `:core:data`'s (shared with the assistant); parsing the typed
 * `DD/MM/AAAA` and `HH:MM` is UI input and stays here.
 */

/** What the admin typed, as typed. */
internal data class NewJamInput(val date: String, val time: String, val venue: String)

/** The values `createJam` is called with: a calendar date, a time of day and the normalized venue. */
internal data class NewJamValues(val date: LocalDate, val time: LocalTime, val venue: String)

/** Why a date cannot be created: not a date, before today, already a jam, or before the upcoming jam. */
internal enum class DateProblem { FORMAT, PAST, EXISTS, EARLIER }

/**
 * The verdict on [NewJamInput]. [values] is set only when every field is usable and the plan is Ready, so
 * `Crear la jam` is enabled exactly then. [earlierThan] names the upcoming jam of an `EARLIER` date;
 * [waitsBehind] names it for a later date, which is created but held back.
 */
internal data class NewJamCheck(
    val dateProblem: DateProblem?,
    val earlierThan: LocalDate?,
    val timeInvalid: Boolean,
    val venueProblem: VenueProblem?,
    val waitsBehind: LocalDate?,
    val values: NewJamValues?,
) {
    val canCreate: Boolean
        get() = values != null
}

/** The time and venue the form opens with (J7): the latest-dated jam's, or 21:00 and nothing. */
internal data class NewJamSeed(val time: LocalTime, val venue: String)

private val DEFAULT_TIME: LocalTime = LocalTime.of(21, 0)

/** The seed from the **latest-dated** jam of the raw snapshot, held back, upcoming or past. */
internal fun newJamSeed(snapshot: JamsSnapshot?): NewJamSeed {
    val latest = snapshot?.let { (listOfNotNull(it.upcoming) + it.heldBack + it.past).maxByOrNull { jam -> jam.date } }
    return if (latest == null) NewJamSeed(DEFAULT_TIME, "") else NewJamSeed(latest.startTime, latest.venue)
}

private val DATE_TEXT = Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})""")
private val TIME_TEXT = Regex("""(\d{1,2})[:.](\d{2})""")
private const val TIME_DIGITS = 2

/** `5/11/2026`, `05-11-2026` and `05.11.2026` are 5 November 2026; anything else or a missing day is null. */
internal fun parseFormDate(text: String): LocalDate? {
    val match = DATE_TEXT.matchEntire(text.trim()) ?: return null
    val (day, month, year) = match.destructured
    return try {
        LocalDate.of(year.toInt(), month.toInt(), day.toInt())
    } catch (ignored: DateTimeException) {
        null
    }
}

/** `21:00`, `21.00` and `9:30` are times of day (normalized to `HH:MM` and checked by `isValidTime`); else null. */
internal fun parseFormTime(text: String): LocalTime? {
    val match = TIME_TEXT.matchEntire(text.trim()) ?: return null
    val (hour, minute) = match.destructured
    val normalized = "${hour.padStart(TIME_DIGITS, '0')}:$minute"
    return if (isValidTime(normalized)) LocalTime.parse(normalized) else null
}

/** The time as the field shows it. */
internal fun formatFormTime(time: LocalTime): String =
    "${time.hour.toString().padStart(TIME_DIGITS, '0')}:${time.minute.toString().padStart(TIME_DIGITS, '0')}"

private fun newJamValues(date: LocalDate?, time: LocalTime?, venue: String?): NewJamValues? =
    if (date == null || time == null || venue == null) null else NewJamValues(date, time, venue)

/** The check of [input] against the raw [snapshot] on [today]. */
internal fun checkNewJam(input: NewJamInput, snapshot: JamsSnapshot?, today: LocalDate): NewJamCheck {
    val date = parseFormDate(input.date)
    val time = parseFormTime(input.time)
    val venue = normalizeVenue(input.venue)
    val plan = date?.let { jamCreationPlan(it, snapshot, today) }
    val blocked = plan as? JamPlan.Blocked
    val ready = plan as? JamPlan.Ready
    val dateProblem = when {
        date == null -> DateProblem.FORMAT
        blocked?.reason == JamPlan.Blocked.Reason.PAST -> DateProblem.PAST
        blocked?.reason == JamPlan.Blocked.Reason.EXISTS -> DateProblem.EXISTS
        blocked?.reason == JamPlan.Blocked.Reason.EARLIER -> DateProblem.EARLIER
        else -> null
    }
    val values = if (ready != null) newJamValues(date, time, venue) else null
    return NewJamCheck(
        dateProblem = dateProblem,
        earlierThan = blocked?.takeIf { it.reason == JamPlan.Blocked.Reason.EARLIER }?.upcomingDate,
        timeInvalid = time == null,
        venueProblem = venueProblem(input.venue),
        waitsBehind = (ready?.heldBack as? JamHeldBack.NewWaits)?.upcomingDate,
        values = values,
    )
}
