package com.bbbjam.core.ui.jamform

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JamPickerValuesTest {
    @Test
    fun `calendar dates use UTC midnight and round trip without shifting a day`() {
        val date = LocalDate.of(2028, 2, 29)
        val midnight = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(midnight, JamPickerValues.dateMillis("29/02/2028"))
        assertEquals("29/02/2028", JamPickerValues.dateText(midnight))
        // UTC midnight is still the previous day in Argentina; decoding in the device zone would be wrong.
        assertEquals(
            date.minusDays(1),
            date.atStartOfDay().atOffset(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(-3)).toLocalDate(),
        )
    }

    @Test
    fun `empty and impossible dates leave the calendar unselected`() {
        assertNull(JamPickerValues.dateMillis(""))
        assertNull(JamPickerValues.dateMillis("29/02/2027"))
        assertNull(JamPickerValues.dateMillis("31/11/2026"))
    }

    @Test
    fun `clock preserves midnight and end of day in 24 hour format`() {
        listOf("00:00", "09:05", "21:00", "23:59").forEach { text ->
            val time = JamPickerValues.time(text)
            assertEquals(text, JamPickerValues.timeText(time.hour, time.minute))
        }
        assertEquals("21:00", JamPickerValues.timeText(JamPickerValues.time("").hour, 0))
    }
}
