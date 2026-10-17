package com.bbbjam.feature.assistant.create

import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.jams.JamDateProblem
import com.bbbjam.core.data.jams.JamFormCheck
import com.bbbjam.core.data.jams.VenueProblem
import com.bbbjam.core.data.jams.checkJamForm
import com.bbbjam.core.ui.jamform.JamFormField
import com.bbbjam.core.ui.jamform.JamFormFieldUiModel
import com.bbbjam.core.ui.jamform.JamFormText
import com.bbbjam.core.ui.jamform.JamFormUiModel
import com.bbbjam.feature.assistant.AssistantHandlers
import com.bbbjam.feature.assistant.apply.ApplyInputs
import com.bbbjam.feature.assistant.jam.JamCopy

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
        title = CreateCopy.FORM_TITLE,
        date = JamFormFieldUiModel(
            label = JamFormText.DATE_LABEL,
            value = input.date,
            hint = JamFormText.DATE_HINT,
            error = dateError(check, shown = input.date.isNotBlank() || form.isTouched(JamFormField.DATE)),
        ),
        time = JamFormFieldUiModel(
            label = JamFormText.TIME_LABEL,
            value = input.time,
            hint = JamFormText.TIME_HINT,
            error = JamFormText.TIME_ERROR.takeIf {
                check.timeInvalid && (input.time.isNotBlank() || form.isTouched(JamFormField.TIME))
            },
        ),
        venue = JamFormFieldUiModel(
            label = JamFormText.VENUE_LABEL,
            value = input.venue,
            hint = null,
            error = venueError(check, touched = form.isTouched(JamFormField.VENUE)),
        ),
        notes = listOfNotNull(
            CreateCopy.formNote(proposal.songs.size),
            JamCopy.DRAFT_LINE,
            check.waitsBehind?.let(CreateCopy::waitsNote),
        ),
        submitLabel = CreateCopy.submit(proposal.songs.size),
        cancelLabel = JamFormText.CANCEL,
        canSubmit = check.canCreate,
        events = handlers.createForm(turnId),
    )
}

private fun dateError(check: JamFormCheck, shown: Boolean): String? = when {
    !shown -> null
    check.dateProblem == JamDateProblem.FORMAT -> JamFormText.DATE_FORMAT_ERROR
    check.dateProblem == JamDateProblem.PAST -> JamFormText.DATE_PAST_ERROR
    check.dateProblem == JamDateProblem.EXISTS -> JamFormText.DATE_EXISTS_ERROR
    check.dateProblem == JamDateProblem.EARLIER -> check.earlierThan?.let(JamFormText::earlierError)
    else -> null
}

private fun venueError(check: JamFormCheck, touched: Boolean): String? = when (check.venueProblem) {
    null -> null
    VenueProblem.EMPTY -> JamFormText.VENUE_EMPTY_ERROR.takeIf { touched }
    VenueProblem.TOO_LONG -> JamFormText.VENUE_LONG_ERROR
    VenueProblem.FORMULA -> JamFormText.VENUE_FORMULA_ERROR
    VenueProblem.CONTROL -> JamFormText.VENUE_EMPTY_ERROR
}
