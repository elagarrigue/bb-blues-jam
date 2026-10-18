package com.bbbjam.feature.pastjams

import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import java.time.LocalDate
import java.time.LocalTime

/** "Sábado 25 de julio de 2026": day, date and year, no time. */
internal fun pastJamDateLabel(date: LocalDate): UiText =
    UiText.res(R.string.past_jams_date_with_year, SpanishDateNames.label(date), date.year)

/** "Sábado 31 de octubre · 21:00": day, date and start time, no year (a jam to come). */
internal fun jamDateLabel(date: LocalDate, startTime: LocalTime): UiText {
    val time = "${startTime.hour.twoDigits()}:${startTime.minute.twoDigits()}"
    return UiText.res(R.string.past_jams_date_with_time, SpanishDateNames.label(date), time)
}

private const val TIME_DIGITS = 2

/** Not `String.format`, which is locale-sensitive. */
private fun Int.twoDigits(): String = toString().padStart(TIME_DIGITS, '0')
