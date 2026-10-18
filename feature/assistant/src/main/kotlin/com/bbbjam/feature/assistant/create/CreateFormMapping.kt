package com.bbbjam.feature.assistant.create

import com.bbbjam.core.assistant.AssistantReply
import com.bbbjam.core.data.jams.JamDateProblem
import com.bbbjam.core.data.jams.JamFormCheck
import com.bbbjam.core.data.jams.VenueProblem
import com.bbbjam.core.data.jams.checkJamForm
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.jamform.JamFormField
import com.bbbjam.core.ui.jamform.JamFormFieldUiModel
import com.bbbjam.core.ui.jamform.JamFormUiModel
import com.bbbjam.core.ui.jamform.jamFormEarlierError
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.AssistantHandlers
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.apply.ApplyInputs

/**
 * The form of "Crear una jam nueva con esta lista" while it is open for [turnId], or null. It is the shared
 * jam form (`core.ui.jamform`): the same rules as the Jams tab's "Nueva jam" (`checkJamForm`), plus the
 * notes of what submitting does. The form is the confirmation: nothing is written until its submit (D-24).
 */
internal fun createForm(
    turnId: Long,
    proposal: AssistantReply.Proposal,
    inputs: ApplyInputs,
    handlers: AssistantHandlers,
): JamFormUiModel? {
    val form = inputs.createForm?.takeIf { it.turnId == turnId }
    val today = inputs.today
    if (form == null || today == null) return null
    val check = checkJamForm(form.input, inputs.snapshot, today)
    val input = form.input
    return JamFormUiModel(
        title = UiText.res(R.string.assistant_create_form_title),
        date = JamFormFieldUiModel(
            label = UiText.res(CoreR.string.jam_form_date_label),
            value = input.date,
            hint = UiText.res(CoreR.string.jam_form_date_hint),
            error = dateError(check, shown = input.date.isNotBlank() || form.isTouched(JamFormField.DATE)),
        ),
        time = JamFormFieldUiModel(
            label = UiText.res(CoreR.string.jam_form_time_label),
            value = input.time,
            hint = UiText.res(CoreR.string.jam_form_time_hint),
            error = UiText.res(CoreR.string.jam_form_time_error).takeIf {
                check.timeInvalid && (input.time.isNotBlank() || form.isTouched(JamFormField.TIME))
            },
        ),
        venue = JamFormFieldUiModel(
            label = UiText.res(CoreR.string.jam_form_venue_label),
            value = input.venue,
            hint = null,
            error = venueError(check, touched = form.isTouched(JamFormField.VENUE)),
        ),
        notes = listOfNotNull(
            createFormNoteText(proposal.songs.size),
            UiText.res(R.string.assistant_jam_draft_line),
            check.waitsBehind?.let(::createWaitsNoteText),
        ),
        submitLabel = createSubmitText(proposal.songs.size),
        cancelLabel = UiText.res(CoreR.string.jam_form_cancel),
        canSubmit = check.canCreate,
        events = handlers.createForm(turnId),
    )
}

private fun dateError(check: JamFormCheck, shown: Boolean): UiText? = when {
    !shown -> null
    check.dateProblem == JamDateProblem.FORMAT -> UiText.res(CoreR.string.jam_form_date_format_error)
    check.dateProblem == JamDateProblem.PAST -> UiText.res(CoreR.string.jam_form_date_past_error)
    check.dateProblem == JamDateProblem.EXISTS -> UiText.res(CoreR.string.jam_form_date_exists_error)
    check.dateProblem == JamDateProblem.EARLIER -> check.earlierThan?.let(::jamFormEarlierError)
    else -> null
}

private fun venueError(check: JamFormCheck, touched: Boolean): UiText? = when (check.venueProblem) {
    null -> null
    VenueProblem.EMPTY -> UiText.res(CoreR.string.jam_form_venue_empty_error).takeIf { touched }
    VenueProblem.TOO_LONG -> UiText.res(CoreR.string.jam_form_venue_long_error)
    VenueProblem.FORMULA -> UiText.res(CoreR.string.jam_form_venue_formula_error)
    VenueProblem.CONTROL -> UiText.res(CoreR.string.jam_form_venue_empty_error)
}
