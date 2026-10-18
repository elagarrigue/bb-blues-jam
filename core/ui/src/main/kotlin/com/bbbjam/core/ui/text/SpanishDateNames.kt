package com.bbbjam.core.ui.text

import com.bbbjam.core.ui.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/**
 * Spanish day and month names shared by every screen that writes a date (Próxima jam, Anteriores).
 * They live in `strings.xml` instead of coming from `DateTimeFormatter` and a `Locale`: desugared
 * java.time on API 24–25 and the JVM may render locale text differently, and the copy must be
 * identical on the device and in tests. Days are capitalized ([day]) because a date label starts
 * with one ("Sábado 25 de julio"), or lower case ([dayLower]) inside a sentence; months are lower
 * case, as Spanish writes them mid-sentence.
 */
object SpanishDateNames {
    fun day(day: DayOfWeek): UiText = UiText.res(
        when (day) {
            DayOfWeek.MONDAY -> R.string.date_day_monday
            DayOfWeek.TUESDAY -> R.string.date_day_tuesday
            DayOfWeek.WEDNESDAY -> R.string.date_day_wednesday
            DayOfWeek.THURSDAY -> R.string.date_day_thursday
            DayOfWeek.FRIDAY -> R.string.date_day_friday
            DayOfWeek.SATURDAY -> R.string.date_day_saturday
            DayOfWeek.SUNDAY -> R.string.date_day_sunday
        },
    )

    fun dayLower(day: DayOfWeek): UiText = UiText.res(
        when (day) {
            DayOfWeek.MONDAY -> R.string.date_day_monday_lower
            DayOfWeek.TUESDAY -> R.string.date_day_tuesday_lower
            DayOfWeek.WEDNESDAY -> R.string.date_day_wednesday_lower
            DayOfWeek.THURSDAY -> R.string.date_day_thursday_lower
            DayOfWeek.FRIDAY -> R.string.date_day_friday_lower
            DayOfWeek.SATURDAY -> R.string.date_day_saturday_lower
            DayOfWeek.SUNDAY -> R.string.date_day_sunday_lower
        },
    )

    fun month(month: Month): UiText = UiText.res(
        when (month) {
            Month.JANUARY -> R.string.date_month_january
            Month.FEBRUARY -> R.string.date_month_february
            Month.MARCH -> R.string.date_month_march
            Month.APRIL -> R.string.date_month_april
            Month.MAY -> R.string.date_month_may
            Month.JUNE -> R.string.date_month_june
            Month.JULY -> R.string.date_month_july
            Month.AUGUST -> R.string.date_month_august
            Month.SEPTEMBER -> R.string.date_month_september
            Month.OCTOBER -> R.string.date_month_october
            Month.NOVEMBER -> R.string.date_month_november
            Month.DECEMBER -> R.string.date_month_december
        },
    )

    /** "Sábado 31 de octubre": day name capitalized, no year. */
    fun label(date: LocalDate): UiText =
        UiText.res(R.string.date_sentence, day(date.dayOfWeek), date.dayOfMonth, month(date.month))

    /** "sábado 31 de octubre": the date inside a sentence, day name in lower case. */
    fun sentence(date: LocalDate): UiText =
        UiText.res(R.string.date_sentence, dayLower(date.dayOfWeek), date.dayOfMonth, month(date.month))
}
