package com.bbbjam.core.data.jams

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime

/*
 * The jam form's rules (`admin-jams-tab` J7/J8, moved here by `assistant-proposal-targets`), pure so a JVM test can
 * check them. Shared by the Jams tab's "Nueva jam" and the assistant's "Crear una jam nueva con esta lista".
 */

/** What the admin typed, as typed. */
data class JamFormInput(val date: String, val time: String, val venue: String)

/** The values `createJam` is called with: a calendar date, a time of day and the normalized venue. */
data class JamFormValues(val date: LocalDate, val time: LocalTime, val venue: String)

/** Why a date cannot be created: not a date, before today, already a jam, or before the upcoming jam. */
enum class JamDateProblem { FORMAT, PAST, EXISTS, EARLIER }

/**
 * The verdict on [JamFormInput]. [values] is set only when every field is usable and the plan is Ready, so
 * `Crear la jam` is enabled exactly then. [earlierThan] names the upcoming jam of an `EARLIER` date;
 * [waitsBehind] names it for a later date, which is created but held back.
 */
data class JamFormCheck(
    val dateProblem: JamDateProblem?,
    val earlierThan: LocalDate?,
    val timeInvalid: Boolean,
    val venueProblem: VenueProblem?,
    val waitsBehind: LocalDate?,
    val values: JamFormValues?,
) {
    val canCreate: Boolean
        get() = values != null
}

/** The time and venue the form opens with (J7): the latest-dated jam's, or 21:00 and nothing. */
data class JamFormSeed(val time: LocalTime, val venue: String)

private val DEFAULT_TIME: LocalTime = LocalTime.of(21, 0)

/** The seed from the **latest-dated** jam of the raw snapshot, held back, upcoming or past. */
fun jamFormSeed(snapshot: JamsSnapshot?): JamFormSeed {
    val latest = snapshot?.let { (listOfNotNull(it.upcoming) + it.heldBack + it.past).maxByOrNull { jam -> jam.date } }
    return if (latest == null) JamFormSeed(DEFAULT_TIME, "") else JamFormSeed(latest.startTime, latest.venue)
}

private val DATE_TEXT = Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})""")
private val TIME_TEXT = Regex("""(\d{1,2})[:.](\d{2})""")
private const val TIME_DIGITS = 2
private const val YEAR_DIGITS = 4

/** `5/11/2026`, `05-11-2026` and `05.11.2026` are 5 November 2026; anything else or a missing day is null. */
fun parseFormDate(text: String): LocalDate? {
    val match = DATE_TEXT.matchEntire(text.trim()) ?: return null
    val (day, month, year) = match.destructured
    return try {
        LocalDate.of(year.toInt(), month.toInt(), day.toInt())
    } catch (ignored: DateTimeException) {
        null
    }
}

/** `21:00`, `21.00` and `9:30` are times of day (normalized to `HH:MM` and checked by `isValidTime`); else null. */
fun parseFormTime(text: String): LocalTime? {
    val match = TIME_TEXT.matchEntire(text.trim()) ?: return null
    val (hour, minute) = match.destructured
    val normalized = "${hour.padStart(TIME_DIGITS, '0')}:$minute"
    return if (isValidTime(normalized)) LocalTime.parse(normalized) else null
}

/** The date as the field shows it: `DD/MM/AAAA`, which [parseFormDate] reads back. */
fun formatFormDate(date: LocalDate): String = "${date.dayOfMonth.toString().padStart(TIME_DIGITS, '0')}/" +
    "${date.monthValue.toString().padStart(TIME_DIGITS, '0')}/${date.year.toString().padStart(YEAR_DIGITS, '0')}"

/** The time as the field shows it. */
fun formatFormTime(time: LocalTime): String =
    "${time.hour.toString().padStart(TIME_DIGITS, '0')}:${time.minute.toString().padStart(TIME_DIGITS, '0')}"

private fun jamFormValues(date: LocalDate?, time: LocalTime?, venue: String?): JamFormValues? =
    if (date == null || time == null || venue == null) null else JamFormValues(date, time, venue)

/** The check of [input] against the raw [snapshot] on [today]. */
fun checkJamForm(input: JamFormInput, snapshot: JamsSnapshot?, today: LocalDate): JamFormCheck {
    val date = parseFormDate(input.date)
    val time = parseFormTime(input.time)
    val venue = normalizeVenue(input.venue)
    val plan = date?.let { jamCreationPlan(it, snapshot, today) }
    val blocked = plan as? JamPlan.Blocked
    val ready = plan as? JamPlan.Ready
    val dateProblem = when {
        date == null -> JamDateProblem.FORMAT
        blocked?.reason == JamPlan.Blocked.Reason.PAST -> JamDateProblem.PAST
        blocked?.reason == JamPlan.Blocked.Reason.EXISTS -> JamDateProblem.EXISTS
        blocked?.reason == JamPlan.Blocked.Reason.EARLIER -> JamDateProblem.EARLIER
        else -> null
    }
    val values = if (ready != null) jamFormValues(date, time, venue) else null
    return JamFormCheck(
        dateProblem = dateProblem,
        earlierThan = blocked?.takeIf { it.reason == JamPlan.Blocked.Reason.EARLIER }?.upcomingDate,
        timeInvalid = time == null,
        venueProblem = venueProblem(input.venue),
        waitsBehind = (ready?.heldBack as? JamHeldBack.NewWaits)?.upcomingDate,
        values = values,
    )
}
