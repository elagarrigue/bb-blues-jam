package com.bbbjam.core.ui.jamform

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** Material's calendar encodes dates at UTC midnight, independent of the device's time zone. */
internal object JamPickerValues {
    private val dateFormat = DateTimeFormatter.ofPattern(
        "dd/MM/uuuu",
        Locale.ROOT,
    ).withResolverStyle(ResolverStyle.STRICT)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
    private const val DEFAULT_HOUR = 21

    fun dateMillis(text: String): Long? = try {
        LocalDate.parse(text, dateFormat).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    } catch (ignored: DateTimeParseException) {
        null
    }

    fun dateText(millis: Long): String = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).format(dateFormat)

    fun time(text: String): LocalTime = try {
        LocalTime.parse(text, timeFormat)
    } catch (ignored: DateTimeParseException) {
        LocalTime.of(DEFAULT_HOUR, 0)
    }

    fun timeText(hour: Int, minute: Int): String = LocalTime.of(hour, minute).format(timeFormat)
}
