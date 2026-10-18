package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.R
import java.time.LocalDate

// The texts of replacing the upcoming jam's list (`assistant-proposal-targets`).

internal fun replaceIntroText(count: Int, date: LocalDate): UiText =
    UiText.plural(R.plurals.assistant_replace_intro, count, count, jamDateText(date))

internal fun replaceRemovedText(titles: List<String>): UiText =
    UiText.plural(R.plurals.assistant_replace_removed_titles, titles.size, titles.size, titles.joinToString(", "))

internal fun replaceLostText(count: Int): UiText = if (count == 0) {
    UiText.res(R.string.assistant_replace_no_assignments)
} else {
    UiText.plural(R.plurals.assistant_replace_lost, count)
}

internal fun replaceKeptText(count: Int): UiText = UiText.plural(R.plurals.assistant_replace_kept, count)

internal fun replaceAddedText(count: Int): UiText = UiText.plural(R.plurals.assistant_replace_added, count)

internal fun replaceEmptyText(count: Int): UiText = UiText.res(R.string.assistant_replace_empty, themesText(count))
