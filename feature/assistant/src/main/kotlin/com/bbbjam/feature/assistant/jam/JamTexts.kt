package com.bbbjam.feature.assistant.jam

import com.bbbjam.core.assistant.JamRejection
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.apply.jamDateText
import com.bbbjam.feature.assistant.run.JamFailure
import java.time.LocalDate
import java.time.LocalTime

// The texts of creating the next jam (`assistant-create-jam`).

/** "Sábado 28 de noviembre de 2026". */
internal fun jamDateInWordsText(date: LocalDate): UiText = UiText.res(
    R.string.assistant_jam_date_in_words,
    SpanishDateNames.day(date.dayOfWeek),
    date.dayOfMonth,
    SpanishDateNames.month(date.month),
    date.year,
)

/** "sábado 28 de noviembre de 2026": the same date inside a sentence. */
internal fun jamDateInWordsLowerText(date: LocalDate): UiText = UiText.res(
    R.string.assistant_jam_date_in_words_lower,
    SpanishDateNames.dayLower(date.dayOfWeek),
    date.dayOfMonth,
    SpanishDateNames.month(date.month),
    date.year,
)

/** "21:00": padded by hand, never `String.format` (locale-sensitive). */
internal fun jamTime(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

internal fun jamDetailText(time: LocalTime, venue: String): UiText =
    UiText.res(R.string.assistant_jam_detail, jamTime(time), venue)

internal fun jamHeldBackNoteText(upcoming: LocalDate): UiText =
    UiText.res(R.string.assistant_jam_held_back_note, jamDateText(upcoming))

internal fun jamBlockedEarlierText(upcoming: LocalDate): UiText =
    UiText.res(R.string.assistant_jam_blocked_earlier, jamDateText(upcoming))

internal fun jamHeldBackLineText(upcoming: LocalDate): UiText =
    UiText.res(R.string.assistant_jam_held_back_line, jamDateText(upcoming))

internal fun jamRejectionText(reason: JamRejection): UiText = UiText.res(
    when (reason) {
        JamRejection.INVALID_DATE -> R.string.assistant_jam_rejected_date
        JamRejection.PAST_DATE -> R.string.assistant_jam_rejected_past
        JamRejection.INVALID_TIME -> R.string.assistant_jam_rejected_time
        JamRejection.INVALID_VENUE -> R.string.assistant_jam_rejected_venue
    },
)

internal fun jamFailureText(failure: JamFailure): UiText = if (failure == JamFailure.ScriptOutdated) {
    UiText.res(R.string.assistant_jam_script_outdated)
} else {
    UiText.res(R.string.assistant_jam_failure, jamFailureReason(failure))
}

/** Why a jam was not created, as a sentence that follows a colon (the outdated script is a full line). */
internal fun jamFailureReason(failure: JamFailure): UiText = when (failure) {
    JamFailure.ScriptOutdated -> UiText.res(R.string.assistant_jam_script_outdated)
    JamFailure.Interrupted -> UiText.res(R.string.assistant_jam_reason_interrupted)
    is JamFailure.Write -> jamWriteReason(failure.reason)
}

private fun jamWriteReason(reason: WriteOutcome): UiText = UiText.res(
    when (reason) {
        WriteOutcome.AccessRefused -> CoreR.string.write_failure_access_refused

        WriteOutcome.Offline -> R.string.assistant_jam_reason_offline

        WriteOutcome.Unavailable, WriteOutcome.Done -> CoreR.string.write_failure_unavailable

        is WriteOutcome.Rejected -> when (reason.rejection) {
            WriteRejection.JAM_EXISTS -> R.string.assistant_jam_reason_jam_exists

            WriteRejection.TAB_EXISTS -> R.string.assistant_jam_reason_tab_exists

            WriteRejection.PAST_DATE -> R.string.assistant_jam_reason_past_date

            WriteRejection.INVALID_DATE, WriteRejection.INVALID_TIME, WriteRejection.INVALID_VENUE ->
                R.string.assistant_jam_reason_invalid_data

            else -> CoreR.string.write_failure_sheet_refused
        }
    },
)
