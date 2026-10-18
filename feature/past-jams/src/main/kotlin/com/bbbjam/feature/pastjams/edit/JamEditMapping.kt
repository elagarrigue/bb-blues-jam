package com.bbbjam.feature.pastjams.edit

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.data.jams.JamFormInput
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.jams.formatFormDate
import com.bbbjam.core.data.jams.formatFormTime
import com.bbbjam.core.data.setlist.jamedit.JamEdit
import com.bbbjam.core.data.setlist.jamstatus.JamStatusChange
import com.bbbjam.core.data.setlist.publish.SetlistPublish
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.jamform.JamFormField
import com.bbbjam.core.ui.jamform.jamFormEarlierError
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.pastjams.R
import com.bbbjam.feature.pastjams.admin.JamsAdminState
import com.bbbjam.feature.pastjams.admin.editFailureMessage
import com.bbbjam.feature.pastjams.create.venueError

/*
 * Editing and deleting a future jam on the Jams tab (`admin-edit-delete-jam`): the card's actions, the edit and
 * delete dialogs and the failure cards, from the raw snapshot and [JamsAdminState].
 */

/** The song count of a jam whose setlist is readable; null when it is not (delete is then not offered). */
private fun Jam.knownSongCount(): Int? = (setlist as? Setlist.Available)?.songs?.size

/** The edit and delete icons of one card, drawn disabled while this jam is being written. */
internal fun JamsAdminState.manageUiModel(jam: Jam): JamManageUiModel {
    val mine = edits.filter { it.jamDate == jam.date }
    val editSending = mine.lastOrNull { it.state == JamEdit.State.Sending }
    val failed = mine.lastOrNull { it.state !is JamEdit.State.Sending }
    val statusBusy = statusChanges.any { it.jamDate == jam.date && it.state == JamStatusChange.State.Sending } ||
        publishes.any { it.jamDate == jam.date && it.state == SetlistPublish.State.Sending }
    val idle = editSending == null && !statusBusy
    return JamManageUiModel(
        edit = IconActionUiModel(
            UiText.res(R.string.past_jams_edit_description, SpanishDateNames.sentence(jam.date)),
            enabled = idle,
        ),
        delete = IconActionUiModel(
            UiText.res(R.string.past_jams_delete_description, SpanishDateNames.sentence(jam.date)),
            enabled = idle,
        )
            .takeIf { jam.knownSongCount() != null },
        sending = editSending?.let {
            UiText.res(
                if (it.change is JamEdit.Change.Delete) R.string.past_jams_deleting else R.string.past_jams_saving,
            )
        },
        failure = failed?.toFailure(),
        events = EventHandler(key = "${jam.date}|${jam.venue}|${jam.startTime}|${failed?.id}|$idle") { event ->
            when (event) {
                JamManageUiModel.Event.Edit -> editing.onOpenEdit(
                    jam.date,
                    JamFormInput(formatFormDate(jam.date), formatFormTime(jam.startTime), jam.venue),
                )

                JamManageUiModel.Event.Delete -> editing.onRequestDelete(jam.date)

                is JamManageUiModel.Event.Retry -> edits.firstOrNull { it.id == event.id }?.let(editing.onRetry)

                is JamManageUiModel.Event.Dismiss -> onDismissEdit(event.id)
            }
        },
    )
}

private fun JamEdit.toFailure(): JamEditFailureUiModel {
    val reason = (state as? JamEdit.State.Failed)?.reason
    val listChanged = (reason as? WriteOutcome.Rejected)?.rejection == WriteRejection.SETLIST_CHANGED
    return JamEditFailureUiModel(
        id = id,
        title = UiText.res(
            if (change is JamEdit.Change.Delete) R.string.past_jams_delete_failed else R.string.past_jams_update_failed,
        ),
        message = if (reason == null) {
            UiText.res(R.string.past_jams_edit_script_outdated)
        } else {
            editFailureMessage(reason)
        },
        consequence = UiText.res(R.string.past_jams_jam_unchanged),
        retryLabel = UiText.res(CoreR.string.list_state_retry).takeIf { reason != null && !listChanged },
        dismissLabel = UiText.res(R.string.past_jams_close),
    )
}

/** The edit dialog of the jam being edited, or null when no dialog is open or that jam is gone. */
internal fun JamsSnapshot.toEditForm(admin: JamsAdminState, future: List<Jam>): EditJamFormUiModel? {
    val jam = future.firstOrNull { it.date.toString() == admin.editing.editingDate } ?: return null
    val input = admin.editing.input
    val check = checkJamEdit(jam, input, this, admin.today)
    val controls = admin.editing
    return EditJamFormUiModel(
        title = UiText.res(R.string.past_jams_edit),
        jamDate = jam.date,
        dateLabel = UiText.res(CoreR.string.jam_form_date_label),
        dateHint = UiText.res(CoreR.string.jam_form_date_hint),
        timeLabel = UiText.res(CoreR.string.jam_form_time_label),
        timeHint = UiText.res(CoreR.string.jam_form_time_hint),
        venueLabel = UiText.res(CoreR.string.jam_form_venue_label),
        date = input.date,
        time = input.time,
        venue = input.venue,
        dateError = editDateError(check, shown = input.date.isNotBlank() || controls.isTouched(JamFormField.DATE)),
        timeError = UiText.res(CoreR.string.jam_form_time_error).takeIf {
            check.timeInvalid && (input.time.isNotBlank() || controls.isTouched(JamFormField.TIME))
        },
        venueError = venueError(check.venueProblem, touched = controls.isTouched(JamFormField.VENUE)),
        dateNote = UiText.res(R.string.past_jams_date_moves_note).takeIf { check.dateChanged },
        visibilityNote = UiText.res(R.string.past_jams_visible_note).takeIf { jam.status != JamStatus.DRAFT },
        saveLabel = UiText.res(R.string.past_jams_save),
        cancelLabel = UiText.res(CoreR.string.jam_form_cancel),
        canSave = check.canSave,
        events = EventHandler(key = "${jam.date}|${check.values}") { event ->
            when (event) {
                is EditJamFormUiModel.Event.Edit -> controls.onEdit(event.field, event.text)
                EditJamFormUiModel.Event.Save -> check.values?.let { controls.onSave(jam.date, it) }
                EditJamFormUiModel.Event.Cancel -> controls.onCancelEdit()
            }
        },
    )
}

private fun editDateError(check: JamEditCheck, shown: Boolean): UiText? {
    val other = check.otherDate
    return when {
        !shown -> null

        check.dateProblem == JamEditDateProblem.FORMAT -> UiText.res(CoreR.string.jam_form_date_format_error)

        check.dateProblem == JamEditDateProblem.PAST -> UiText.res(CoreR.string.jam_form_date_past_error)

        check.dateProblem == JamEditDateProblem.EXISTS -> UiText.res(CoreR.string.jam_form_date_exists_error)

        check.dateProblem == JamEditDateProblem.LATER ->
            other?.let { UiText.res(R.string.past_jams_later_error, SpanishDateNames.sentence(it)) }

        check.dateProblem == JamEditDateProblem.EARLIER ->
            other?.let { UiText.res(R.string.past_jams_earlier_than_upcoming_error, SpanishDateNames.sentence(it)) }

        else -> null
    }
}

/** The delete confirmation of the jam being deleted, or null when none is open or its count is unknown. */
internal fun JamsAdminState.toDeleteConfirm(future: List<Jam>): DeleteJamUiModel? {
    val jam = future.firstOrNull { it.date.toString() == editing.deletingDate }
    val songs = jam?.knownSongCount()
    return if (jam == null || songs == null) null else deleteConfirmation(jam, songs, future)
}

private fun JamsAdminState.deleteConfirmation(jam: Jam, songs: Int, future: List<Jam>): DeleteJamUiModel {
    val upcoming = future.first()
    val nextLine = if (jam.date == upcoming.date) {
        future.getOrNull(1)?.let {
            UiText.res(R.string.past_jams_next_jam_becomes, SpanishDateNames.sentence(it.date))
        } ?: UiText.res(R.string.past_jams_no_jam_left)
    } else {
        UiText.res(R.string.past_jams_next_jam_stays, SpanishDateNames.sentence(upcoming.date))
    }
    val statusLine = when (jam.status) {
        JamStatus.PUBLISHED -> UiText.res(R.string.past_jams_published_lost)
        JamStatus.CREATED -> UiText.res(R.string.past_jams_created_lost)
        JamStatus.DRAFT -> null
    }
    return DeleteJamUiModel(
        title = UiText.res(R.string.past_jams_delete_title, SpanishDateNames.sentence(jam.date)),
        jamDate = jam.date,
        lines = listOfNotNull(songsLost(songs), statusLine, nextLine, UiText.res(R.string.past_jams_undo_warning)),
        confirmLabel = UiText.res(R.string.past_jams_delete),
        cancelLabel = UiText.res(CoreR.string.jam_form_cancel),
        events = EventHandler(key = "${jam.date}|$songs") { event ->
            when (event) {
                DeleteJamUiModel.Event.Confirm -> editing.onConfirmDelete(jam.date, songs)
                DeleteJamUiModel.Event.Cancel -> editing.onCancelDelete()
            }
        },
    )
}

/** What deleting loses: an empty list is a line of its own, never "0 temas". */
private fun songsLost(count: Int): UiText =
    if (count == 0) UiText.res(R.string.past_jams_empty_list) else UiText.plural(R.plurals.past_jams_songs_lost, count)
