package com.bbbjam.feature.pastjams.create

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.data.jams.JamDateProblem
import com.bbbjam.core.data.jams.JamFormCheck
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.jams.VenueProblem
import com.bbbjam.core.data.jams.checkJamForm
import com.bbbjam.core.data.jams.jamFormSeed
import com.bbbjam.core.data.setlist.jamcreate.CreateJamOutcome
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.jamform.JamFormField
import com.bbbjam.core.ui.jamform.JamFormFieldUiModel
import com.bbbjam.core.ui.jamform.JamFormUiModel
import com.bbbjam.core.ui.jamform.jamFormEarlierError
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.pastjams.R
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
        label = UiText.res(R.string.past_jams_new_jam),
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
        title = UiText.res(R.string.past_jams_new_jam),
        date = JamFormFieldUiModel(
            label = UiText.res(CoreR.string.jam_form_date_label),
            value = input.date,
            hint = UiText.res(CoreR.string.jam_form_date_hint),
            error = dateError(check, shown = input.date.isNotBlank() || controls.isTouched(JamFormField.DATE)),
        ),
        time = JamFormFieldUiModel(
            label = UiText.res(CoreR.string.jam_form_time_label),
            value = input.time,
            hint = UiText.res(CoreR.string.jam_form_time_hint),
            error = UiText.res(CoreR.string.jam_form_time_error).takeIf {
                check.timeInvalid && (input.time.isNotBlank() || controls.isTouched(JamFormField.TIME))
            },
        ),
        venue = JamFormFieldUiModel(
            label = UiText.res(CoreR.string.jam_form_venue_label),
            value = input.venue,
            hint = null,
            error = venueError(check.venueProblem, touched = controls.isTouched(JamFormField.VENUE)),
        ),
        notes = listOfNotNull(
            check.waitsBehind?.let { UiText.res(R.string.past_jams_waits_note, SpanishDateNames.sentence(it)) },
            UiText.res(R.string.past_jams_new_jam_draft_note),
        ),
        submitLabel = UiText.res(R.string.past_jams_new_jam_create),
        cancelLabel = UiText.res(CoreR.string.jam_form_cancel),
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

private fun dateError(check: JamFormCheck, shown: Boolean): UiText? = when {
    !shown -> null
    check.dateProblem == JamDateProblem.FORMAT -> UiText.res(CoreR.string.jam_form_date_format_error)
    check.dateProblem == JamDateProblem.PAST -> UiText.res(CoreR.string.jam_form_date_past_error)
    check.dateProblem == JamDateProblem.EXISTS -> UiText.res(CoreR.string.jam_form_date_exists_error)
    check.dateProblem == JamDateProblem.EARLIER -> check.earlierThan?.let(::jamFormEarlierError)
    else -> null
}

internal fun venueError(problem: VenueProblem?, touched: Boolean): UiText? = when (problem) {
    null -> null

    VenueProblem.EMPTY -> UiText.res(CoreR.string.jam_form_venue_empty_error).takeIf { touched }

    VenueProblem.TOO_LONG -> UiText.res(CoreR.string.jam_form_venue_long_error)

    VenueProblem.FORMULA -> UiText.res(CoreR.string.jam_form_venue_formula_error)

    // A soft keyboard's single-line field cannot type one; the generic line is the honest answer.
    VenueProblem.CONTROL -> UiText.res(CoreR.string.jam_form_venue_empty_error)
}

private fun NewJamProgress.toUiModel(): NewJamProgressUiModel = when (this) {
    NewJamProgress.Creating -> NewJamProgressUiModel.Creating(UiText.res(R.string.past_jams_creating))

    NewJamProgress.Created -> NewJamProgressUiModel.Created(UiText.res(R.string.past_jams_created))

    is NewJamProgress.Failed -> NewJamProgressUiModel.Failed(
        title = UiText.res(R.string.past_jams_create_failed),
        message = createFailureMessage(outcome),
        consequence = UiText.res(R.string.past_jams_create_consequence),
        retryLabel = UiText.res(CoreR.string.list_state_retry).takeIf { outcome !is CreateJamOutcome.ScriptOutdated },
        dismissLabel = UiText.res(R.string.past_jams_close),
    )
}

/** The failed creation's reason: the create codes first, then the shared outcomes. */
internal fun createFailureMessage(outcome: CreateJamOutcome): UiText = when (outcome) {
    is CreateJamOutcome.ScriptOutdated -> UiText.res(R.string.past_jams_create_script_outdated)

    is CreateJamOutcome.NotCreated -> outcome.reason.let { reason ->
        val rejected = reason as? WriteOutcome.Rejected
        when {
            rejected?.rejection == WriteRejection.JAM_EXISTS || rejected?.rejection == WriteRejection.TAB_EXISTS ->
                UiText.res(R.string.past_jams_create_exists)

            rejected?.rejection == WriteRejection.PAST_DATE -> UiText.res(CoreR.string.jam_form_date_past_error)

            rejected?.isInvalidInput == true -> UiText.res(R.string.past_jams_create_invalid)

            else -> failureMessage(reason)
        }
    }

    // Created never reaches a failure; the generic line is the safe fallback.
    is CreateJamOutcome.Created -> UiText.res(CoreR.string.write_failure_unavailable)
}
