package com.bbbjam.core.ui.jamform

import com.bbbjam.core.ui.R
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import java.time.LocalDate

/**
 * The error of a date before the upcoming jam, which would put that jam on hold. The field labels,
 * hints and the other field errors every jam form shares are `jam_form_*` string resources.
 */
fun jamFormEarlierError(upcoming: LocalDate): UiText =
    UiText.res(R.string.jam_form_earlier_error, SpanishDateNames.sentence(upcoming))
