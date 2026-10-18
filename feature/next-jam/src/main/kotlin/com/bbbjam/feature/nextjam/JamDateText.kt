package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
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
internal fun timeRemaining(today: LocalDate, date: LocalDate, startTime: LocalTime): UiText {
    val days = ChronoUnit.DAYS.between(today, date).coerceAtLeast(0)
    return when (days) {
        0L -> UiText.res(if (startTime >= EVENING_STARTS) R.string.next_jam_tonight else R.string.next_jam_today)
        1L -> UiText.res(R.string.next_jam_tomorrow)
        else -> UiText.res(R.string.next_jam_in_days, days.toInt())
    }
}

/** "Sábado 31 de octubre · 21:00": day, date and start time, no year. */
internal fun jamDateLabel(date: LocalDate, startTime: LocalTime): UiText {
    val time = "${startTime.hour.twoDigits()}:${startTime.minute.twoDigits()}"
    return UiText.res(R.string.next_jam_date_time, SpanishDateNames.label(date), time)
}

/** Not `String.format`, which is locale-sensitive. */
private fun Int.twoDigits(): String = toString().padStart(TIME_DIGITS, '0')
