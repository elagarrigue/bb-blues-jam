package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.R
import java.time.LocalDate

// The texts of loading a proposal into the upcoming jam (`assistant-apply-proposal`), built from the
// module's strings.xml. Pure: they only name resources and arguments, so presenters stay testable on the JVM.

/** "sábado 31 de octubre": the day in lower case, as a sentence needs it. */
internal fun jamDateText(date: LocalDate): UiText = SpanishDateNames.sentence(date)

/** "1 tema", "3 temas". */
internal fun themesText(count: Int): UiText = UiText.plural(R.plurals.assistant_apply_themes, count)

internal fun applyIntroText(count: Int, date: LocalDate): UiText =
    UiText.plural(R.plurals.assistant_apply_add_intro, count, count, jamDateText(date))

internal fun applyExistingText(count: Int): UiText =
    UiText.res(R.string.assistant_apply_add_existing, themesText(count))

internal fun applySkippedText(titles: List<String>): UiText =
    UiText.plural(R.plurals.assistant_apply_add_skipped, titles.size, titles.size, titles.joinToString(", "))

internal fun applyConfirmText(count: Int): UiText = UiText.res(R.string.assistant_apply_add_confirm, themesText(count))
