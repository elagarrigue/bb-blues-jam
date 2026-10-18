package com.bbbjam.feature.assistant.create

import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.apply.jamDateText
import com.bbbjam.feature.assistant.apply.themesText
import java.time.LocalDate

// The texts of creating a jam with the proposal's list (`assistant-proposal-targets`).

internal fun createFormNoteText(count: Int): UiText = UiText.plural(R.plurals.assistant_create_form_note, count)

internal fun createWaitsNoteText(upcoming: LocalDate): UiText =
    UiText.res(R.string.assistant_create_waits_note, jamDateText(upcoming))

internal fun createSubmitText(count: Int): UiText = UiText.res(R.string.assistant_create_submit, themesText(count))

internal fun createLoadingText(date: LocalDate, done: Int, total: Int): UiText =
    UiText.res(R.string.assistant_create_loading, jamDateText(date), done, total)

internal fun createSuccessText(date: LocalDate, count: Int, behind: LocalDate?): UiText = if (behind == null) {
    UiText.plural(R.plurals.assistant_create_success, count, count, jamDateText(date))
} else {
    UiText.plural(R.plurals.assistant_create_success_waiting, count, count, jamDateText(date), jamDateText(behind))
}

internal fun createNotCreatedText(reason: UiText): UiText = UiText.res(R.string.assistant_create_not_created, reason)

internal fun createPartialText(date: LocalDate, added: Int, planned: Int, detail: UiText): UiText =
    UiText.res(R.string.assistant_create_partial, jamDateText(date), added, themesText(planned), detail)

internal fun createStoppedText(date: LocalDate, added: Int, planned: Int): UiText =
    UiText.res(R.string.assistant_create_stopped, jamDateText(date), added, themesText(planned))

internal fun createInterruptedText(date: LocalDate, added: Int, planned: Int): UiText =
    UiText.res(R.string.assistant_create_interrupted, jamDateText(date), added, themesText(planned))
