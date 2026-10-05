package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.text.SpanishDateNames
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** A jam starting at or after this time is "esta noche" on its own date; earlier, "hoy". */
private val EVENING_STARTS: LocalTime = LocalTime.of(18, 0)

private const val TIME_DIGITS = 2

/**
 * How long until a jam on [date] starting at [startTime], seen on [today], in calendar days.
 * [today] must come from `JamCalendar.today()` (Buenos Aires, P7), never from the device or UTC.
 * A negative distance, only possible in a race at midnight, counts as the jam's own date.
 */
internal fun timeRemaining(today: LocalDate, date: LocalDate, startTime: LocalTime): String {
    val days = ChronoUnit.DAYS.between(today, date).coerceAtLeast(0)
    return when (days) {
        0L -> if (startTime >= EVENING_STARTS) NextJamCopy.TONIGHT else NextJamCopy.TODAY
        1L -> NextJamCopy.TOMORROW
        else -> NextJamCopy.inDays(days)
    }
}

/** "Sábado 31 de octubre · 21:00": day, date and start time, no year. */
internal fun jamDateLabel(date: LocalDate, startTime: LocalTime): String {
    val day = SpanishDateNames.day(date.dayOfWeek)
    val month = SpanishDateNames.month(date.month)
    val time = "${startTime.hour.twoDigits()}:${startTime.minute.twoDigits()}"
    return "$day ${date.dayOfMonth} de $month · $time"
}

/** Not `String.format`, which is locale-sensitive. */
private fun Int.twoDigits(): String = toString().padStart(TIME_DIGITS, '0')
