package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.UiText
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The expected texts are `UiText` values written out with their resource ids and arguments
 * (`docs/specs/next-jam-read-only-list.md`), not built by [timeRemaining] or [jamDateLabel], so a change
 * of id or of arguments fails here.
 */
class JamDateTextTest {
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val nine = LocalTime.of(21, 0)

    private val tonight = UiText.res(R.string.next_jam_tonight)
    private val today = UiText.res(R.string.next_jam_today)
    private val tomorrow = UiText.res(R.string.next_jam_tomorrow)

    /** Monday first, as `DayOfWeek` is. */
    private val daysOfWeek = listOf(
        CoreR.string.date_day_monday,
        CoreR.string.date_day_tuesday,
        CoreR.string.date_day_wednesday,
        CoreR.string.date_day_thursday,
        CoreR.string.date_day_friday,
        CoreR.string.date_day_saturday,
        CoreR.string.date_day_sunday,
    )

    private fun inDays(days: Int) = UiText.res(R.string.next_jam_in_days, days)

    /** "Sábado 31 de octubre · 21:00", from the day and month resources. */
    private fun label(day: Int, dayOfMonth: Int, month: Int, time: String) = UiText.res(
        R.string.next_jam_date_time,
        UiText.res(CoreR.string.date_sentence, UiText.res(day), dayOfMonth, UiText.res(month)),
        time,
    )

    @Test
    fun `on its own date a jam starting at 18 00 or later is tonight, earlier is today`() {
        assertEquals(tonight, timeRemaining(jamDate, jamDate, nine))
        assertEquals(tonight, timeRemaining(jamDate, jamDate, LocalTime.of(18, 0)))
        assertEquals(today, timeRemaining(jamDate, jamDate, LocalTime.of(17, 59)))
        assertEquals(today, timeRemaining(jamDate, jamDate, LocalTime.of(11, 0)))
    }

    @Test
    fun `the day before is tomorrow and later days are counted`() {
        assertEquals(tomorrow, timeRemaining(jamDate.minusDays(1), jamDate, nine))
        assertEquals(tomorrow, timeRemaining(jamDate.minusDays(1), jamDate, LocalTime.of(10, 0)))
        assertEquals(inDays(2), timeRemaining(jamDate.minusDays(2), jamDate, nine))
        assertEquals(inDays(29), timeRemaining(LocalDate.of(2026, 10, 2), jamDate, nine))
        assertEquals(inDays(45), timeRemaining(LocalDate.of(2026, 9, 16), jamDate, nine))
    }

    @Test
    fun `days are calendar days across a year change`() {
        assertEquals(inDays(2), timeRemaining(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 2), nine))
        assertEquals(tomorrow, timeRemaining(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1), nine))
    }

    @Test
    fun `a jam already behind today clamps to its own date`() {
        assertEquals(tonight, timeRemaining(jamDate.plusDays(1), jamDate, nine))
        assertEquals(today, timeRemaining(jamDate.plusDays(1), jamDate, LocalTime.of(12, 0)))
    }

    @Test
    fun `the date label names the day and month in Spanish with a padded start time`() {
        assertEquals(
            label(CoreR.string.date_day_saturday, 31, CoreR.string.date_month_october, "21:00"),
            jamDateLabel(jamDate, nine),
        )
        assertEquals(
            label(CoreR.string.date_day_monday, 1, CoreR.string.date_month_february, "09:05"),
            jamDateLabel(LocalDate.of(2027, 2, 1), LocalTime.of(9, 5)),
        )
    }

    @Test
    fun `every day of the week has its Spanish name`() {
        // 2026-10-19 is a Monday.
        daysOfWeek.forEachIndexed { offset, day ->
            val date = LocalDate.of(2026, 10, 19).plusDays(offset.toLong())
            assertEquals(
                label(day, date.dayOfMonth, CoreR.string.date_month_october, "21:00"),
                jamDateLabel(date, nine),
            )
        }
    }

    @Test
    fun `every month has its lowercase Spanish name`() {
        val months = listOf(
            CoreR.string.date_month_january,
            CoreR.string.date_month_february,
            CoreR.string.date_month_march,
            CoreR.string.date_month_april,
            CoreR.string.date_month_may,
            CoreR.string.date_month_june,
            CoreR.string.date_month_july,
            CoreR.string.date_month_august,
            CoreR.string.date_month_september,
            CoreR.string.date_month_october,
            CoreR.string.date_month_november,
            CoreR.string.date_month_december,
        )
        months.forEachIndexed { index, month ->
            val date = LocalDate.of(2027, index + 1, 10)
            val day = daysOfWeek[date.dayOfWeek.ordinal]
            assertEquals(label(day, 10, month, "21:00"), jamDateLabel(date, nine))
        }
    }
}
