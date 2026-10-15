package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.jams.VenueProblem
import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.ui.presenter.EventHandler
import java.time.LocalDate

/**
 * The "Nueva jam" part of the Jams tab (`admin-jams-tab`, J7/J9) from the raw snapshot, [controls] and
 * [today] (`JamCalendar.today()`, read once per snapshot). Hora and lugar open seeded from the latest jam.
 * A blank field shows no error until it has been edited; a typed one shows its error at once.
 */
internal fun JamsSnapshot.toNewJam(controls: NewJamControls, today: LocalDate): NewJamUiModel {
    val check = checkNewJam(controls.input, this, today)
    return NewJamUiModel(
        label = PastJamsCopy.NEW_JAM,
        form = if (controls.isOpen) toForm(controls, check) else null,
        progress = controls.progress?.toUiModel(),
        events = EventHandler { event ->
            when (event) {
                NewJamUiModel.Event.Open -> controls.onOpen(newJamSeed(this))
                NewJamUiModel.Event.Cancel -> controls.onCancel()
                is NewJamUiModel.Event.Edit -> controls.onEdit(event.field, event.text)
                NewJamUiModel.Event.Create -> check.values?.let(controls.onCreate)
                NewJamUiModel.Event.Retry -> controls.onRetry()
                NewJamUiModel.Event.Dismiss -> controls.onDismiss()
            }
        },
    )
}

private fun toForm(controls: NewJamControls, check: NewJamCheck): NewJamFormUiModel {
    val input = controls.input
    return NewJamFormUiModel(
        title = PastJamsCopy.NEW_JAM,
        dateLabel = PastJamsCopy.NEW_JAM_DATE,
        dateHint = PastJamsCopy.NEW_JAM_DATE_HINT,
        timeLabel = PastJamsCopy.NEW_JAM_TIME,
        timeHint = PastJamsCopy.NEW_JAM_TIME_HINT,
        venueLabel = PastJamsCopy.NEW_JAM_VENUE,
        date = input.date,
        time = input.time,
        venue = input.venue,
        dateError = dateError(check, shown = input.date.isNotBlank() || controls.isTouched(NewJamField.DATE)),
        timeError = PastJamsCopy.TIME_ERROR.takeIf {
            check.timeInvalid && (input.time.isNotBlank() || controls.isTouched(NewJamField.TIME))
        },
        venueError = venueError(check.venueProblem, touched = controls.isTouched(NewJamField.VENUE)),
        waitsNote = check.waitsBehind?.let(PastJamsCopy::waitsNote),
        draftNote = PastJamsCopy.NEW_JAM_DRAFT_NOTE,
        createLabel = PastJamsCopy.NEW_JAM_CREATE,
        cancelLabel = PastJamsCopy.CANCEL,
        canCreate = check.canCreate,
    )
}

private fun dateError(check: NewJamCheck, shown: Boolean): String? = when {
    !shown -> null
    check.dateProblem == DateProblem.FORMAT -> PastJamsCopy.DATE_FORMAT_ERROR
    check.dateProblem == DateProblem.PAST -> PastJamsCopy.DATE_PAST_ERROR
    check.dateProblem == DateProblem.EXISTS -> PastJamsCopy.DATE_EXISTS_ERROR
    check.dateProblem == DateProblem.EARLIER -> check.earlierThan?.let(PastJamsCopy::earlierError)
    else -> null
}

private fun venueError(problem: VenueProblem?, touched: Boolean): String? = when (problem) {
    null -> null

    VenueProblem.EMPTY -> PastJamsCopy.VENUE_EMPTY_ERROR.takeIf { touched }

    VenueProblem.TOO_LONG -> PastJamsCopy.VENUE_LONG_ERROR

    VenueProblem.FORMULA -> PastJamsCopy.VENUE_FORMULA_ERROR

    // A soft keyboard's single-line field cannot type one; the generic line is the honest answer.
    VenueProblem.CONTROL -> PastJamsCopy.VENUE_EMPTY_ERROR
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
            code == "past_date" -> PastJamsCopy.DATE_PAST_ERROR
            code.startsWith("invalid_") -> PastJamsCopy.CREATE_INVALID
            else -> failureMessage(reason)
        }
    }

    // Created never reaches a failure; the generic line is the safe fallback.
    is CreateJamOutcome.Created -> PastJamsCopy.UNAVAILABLE
}
