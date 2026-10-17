package com.bbbjam.feature.pastjams.create

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamDateProblem
import com.bbbjam.core.data.jams.JamFormCheck
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.jams.VenueProblem
import com.bbbjam.core.data.jams.checkJamForm
import com.bbbjam.core.data.jams.jamFormSeed
import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.ui.jamform.JamFormField
import com.bbbjam.core.ui.jamform.JamFormFieldUiModel
import com.bbbjam.core.ui.jamform.JamFormText
import com.bbbjam.core.ui.jamform.JamFormUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.feature.pastjams.PastJamsCopy
import com.bbbjam.feature.pastjams.admin.failureMessage
import com.bbbjam.feature.pastjams.toUiModel
import java.time.LocalDate

/**
 * The "Nueva jam" part of the Jams tab (`admin-jams-tab`, J7/J9) from the raw snapshot, [controls] and
 * [today] (`JamCalendar.today()`, read once per snapshot). Hora and lugar open seeded from the latest jam.
 * A blank field shows no error until it has been edited; a typed one shows its error at once.
 */
internal fun JamsSnapshot.toNewJam(controls: NewJamControls, today: LocalDate): NewJamUiModel {
    val check = checkJamForm(controls.input, this, today)
    val handle: (NewJamUiModel.Event) -> Unit = { event ->
        when (event) {
            NewJamUiModel.Event.Open -> controls.onOpen(jamFormSeed(this))
            NewJamUiModel.Event.Cancel -> controls.onCancel()
            is NewJamUiModel.Event.Edit -> controls.onEdit(event.field, event.text)
            NewJamUiModel.Event.Create -> check.values?.let(controls.onCreate)
            NewJamUiModel.Event.Retry -> controls.onRetry()
            NewJamUiModel.Event.Dismiss -> controls.onDismiss()
        }
    }
    return NewJamUiModel(
        label = PastJamsCopy.NEW_JAM,
        form = if (controls.isOpen) toForm(controls, check, handle) else null,
        progress = controls.progress?.toUiModel(),
        events = EventHandler(handle = handle),
    )
}

private fun toForm(
    controls: NewJamControls,
    check: JamFormCheck,
    handle: (NewJamUiModel.Event) -> Unit,
): JamFormUiModel {
    val input = controls.input
    return JamFormUiModel(
        title = PastJamsCopy.NEW_JAM,
        date = JamFormFieldUiModel(
            label = JamFormText.DATE_LABEL,
            value = input.date,
            hint = JamFormText.DATE_HINT,
            error = dateError(check, shown = input.date.isNotBlank() || controls.isTouched(JamFormField.DATE)),
        ),
        time = JamFormFieldUiModel(
            label = JamFormText.TIME_LABEL,
            value = input.time,
            hint = JamFormText.TIME_HINT,
            error = JamFormText.TIME_ERROR.takeIf {
                check.timeInvalid && (input.time.isNotBlank() || controls.isTouched(JamFormField.TIME))
            },
        ),
        venue = JamFormFieldUiModel(
            label = JamFormText.VENUE_LABEL,
            value = input.venue,
            hint = null,
            error = venueError(check.venueProblem, touched = controls.isTouched(JamFormField.VENUE)),
        ),
        notes = listOfNotNull(check.waitsBehind?.let(PastJamsCopy::waitsNote), PastJamsCopy.NEW_JAM_DRAFT_NOTE),
        submitLabel = PastJamsCopy.NEW_JAM_CREATE,
        cancelLabel = JamFormText.CANCEL,
        canSubmit = check.canCreate,
        events = EventHandler { event ->
            handle(
                when (event) {
                    is JamFormUiModel.Event.Edit -> NewJamUiModel.Event.Edit(event.field, event.text)
                    JamFormUiModel.Event.Submit -> NewJamUiModel.Event.Create
                    JamFormUiModel.Event.Cancel -> NewJamUiModel.Event.Cancel
                },
            )
        },
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

internal fun venueError(problem: VenueProblem?, touched: Boolean): String? = when (problem) {
    null -> null

    VenueProblem.EMPTY -> JamFormText.VENUE_EMPTY_ERROR.takeIf { touched }

    VenueProblem.TOO_LONG -> JamFormText.VENUE_LONG_ERROR

    VenueProblem.FORMULA -> JamFormText.VENUE_FORMULA_ERROR

    // A soft keyboard's single-line field cannot type one; the generic line is the honest answer.
    VenueProblem.CONTROL -> JamFormText.VENUE_EMPTY_ERROR
}

private fun NewJamProgress.toUiModel(): NewJamProgressUiModel = when (this) {
    NewJamProgress.Creating -> NewJamProgressUiModel.Creating(PastJamsCopy.CREATING)

    NewJamProgress.Created -> NewJamProgressUiModel.Created(PastJamsCopy.CREATED)

    is NewJamProgress.Failed -> NewJamProgressUiModel.Failed(
        title = PastJamsCopy.CREATE_FAILED,
        message = createFailureMessage(outcome),
        consequence = PastJamsCopy.CREATE_CONSEQUENCE,
        retryLabel = PastJamsCopy.PUBLISH_RETRY.takeIf { outcome !is CreateJamOutcome.ScriptOutdated },
        dismissLabel = PastJamsCopy.CLOSE,
    )
}

/** The failed creation's reason: the create codes first, then the shared outcomes. */
internal fun createFailureMessage(outcome: CreateJamOutcome): String = when (outcome) {
    is CreateJamOutcome.ScriptOutdated -> PastJamsCopy.CREATE_SCRIPT_OUTDATED

    is CreateJamOutcome.NotCreated -> outcome.reason.let { reason ->
        val code = (reason as? WriteOutcome.Rejected)?.code.orEmpty()
        when {
            code == "jam_exists" || code == "tab_exists" -> PastJamsCopy.CREATE_EXISTS
            code == "past_date" -> JamFormText.DATE_PAST_ERROR
            code.startsWith("invalid_") -> PastJamsCopy.CREATE_INVALID
            else -> failureMessage(reason)
        }
    }

    // Created never reaches a failure; the generic line is the safe fallback.
    is CreateJamOutcome.Created -> PastJamsCopy.UNAVAILABLE
}
