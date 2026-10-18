package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import java.time.LocalDate

/*
 * Texts several tests expect, written out with the resource ids (never read back from production
 * code), so a change of id or of arguments fails a test. The wording itself is `strings.xml`.
 */

/** What a screen reader says for [key]: "Tonalidad Bm". */
internal fun keyDescription(key: String): UiText = UiText.res(CoreR.string.song_list_key_description, key)

/** "Guardando…": the line under a song whose write is sending. */
internal val savingText: UiText = UiText.res(R.string.next_jam_saving)

/** "Sábado 31 de octubre · 21:00": the header's date label. */
internal fun dateTimeLabel(date: LocalDate, time: String = "21:00"): UiText =
    UiText.res(R.string.next_jam_date_time, SpanishDateNames.label(date), time)

/** The header of a jam on 31 October 2026 at 21:00, seen on 2 October: "En 29 días". */
internal val octoberThirtyFirstHeader = JamHeaderUiModel(
    date = dateTimeLabel(LocalDate.of(2026, 10, 31)),
    venue = "La Macanuda",
    timeRemaining = UiText.res(R.string.next_jam_in_days, 29),
)

/** The draft card musicians see in place of the rows of a jam that is not published. */
internal val draftCard = DraftSetlistUiModel(
    label = UiText.res(R.string.next_jam_draft_label),
    title = UiText.res(R.string.next_jam_draft_title),
    message = UiText.res(R.string.next_jam_draft_message),
)

/** The skeleton state. */
internal val nextJamLoading = NextJamUiModel.Loading(UiText.res(R.string.next_jam_loading))
