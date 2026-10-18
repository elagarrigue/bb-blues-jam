package com.bbbjam.feature.pastjams.edit

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.data.jams.JamFormInput
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.jams.formatFormDate
import com.bbbjam.core.data.jams.formatFormTime
import com.bbbjam.core.data.setlist.JamEdit
import com.bbbjam.core.data.setlist.JamStatusChange
import com.bbbjam.core.data.setlist.SetlistPublish
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.jamform.JamFormField
import com.bbbjam.core.ui.jamform.JamFormText
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.feature.pastjams.PastJamsCopy
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
        edit = IconActionUiModel(JamEditCopy.editDescription(jam.date), enabled = idle),
        delete = IconActionUiModel(JamEditCopy.deleteDescription(jam.date), enabled = idle)
            .takeIf { jam.knownSongCount() != null },
        sending = editSending?.let {
            if (it.change is JamEdit.Change.Delete) JamEditCopy.DELETING else JamEditCopy.SAVING
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
        title = if (change is JamEdit.Change.Delete) JamEditCopy.DELETE_FAILED else JamEditCopy.UPDATE_FAILED,
        message = if (reason == null) JamEditCopy.SCRIPT_OUTDATED else editFailureMessage(reason),
        consequence = JamEditCopy.CONSEQUENCE,
        retryLabel = PastJamsCopy.PUBLISH_RETRY.takeIf { reason != null && !listChanged },
        dismissLabel = PastJamsCopy.CLOSE,
    )
}

/** The edit dialog of the jam being edited, or null when no dialog is open or that jam is gone. */
internal fun JamsSnapshot.toEditForm(admin: JamsAdminState, future: List<Jam>): EditJamFormUiModel? {
    val jam = future.firstOrNull { it.date.toString() == admin.editing.editingDate } ?: return null
    val input = admin.editing.input
    val check = checkJamEdit(jam, input, this, admin.today)
    val controls = admin.editing
    return EditJamFormUiModel(
        title = JamEditCopy.EDIT,
        jamDate = jam.date,
        dateLabel = JamFormText.DATE_LABEL,
        dateHint = JamFormText.DATE_HINT,
        timeLabel = JamFormText.TIME_LABEL,
        timeHint = JamFormText.TIME_HINT,
        venueLabel = JamFormText.VENUE_LABEL,
        date = input.date,
        time = input.time,
        venue = input.venue,
        dateError = editDateError(check, shown = input.date.isNotBlank() || controls.isTouched(JamFormField.DATE)),
        timeError = JamFormText.TIME_ERROR.takeIf {
            check.timeInvalid && (input.time.isNotBlank() || controls.isTouched(JamFormField.TIME))
        },
        venueError = venueError(check.venueProblem, touched = controls.isTouched(JamFormField.VENUE)),
        dateNote = JamEditCopy.DATE_MOVES_NOTE.takeIf { check.dateChanged },
        visibilityNote = JamEditCopy.VISIBLE_NOTE.takeIf { jam.status != JamStatus.DRAFT },
        saveLabel = JamEditCopy.SAVE,
        cancelLabel = PastJamsCopy.CANCEL,
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

private fun editDateError(check: JamEditCheck, shown: Boolean): String? {
    val other = check.otherDate
    return when {
        !shown -> null
        check.dateProblem == JamEditDateProblem.FORMAT -> JamFormText.DATE_FORMAT_ERROR
        check.dateProblem == JamEditDateProblem.PAST -> JamFormText.DATE_PAST_ERROR
        check.dateProblem == JamEditDateProblem.EXISTS -> JamFormText.DATE_EXISTS_ERROR
        check.dateProblem == JamEditDateProblem.LATER -> other?.let(JamEditCopy::laterError)
        check.dateProblem == JamEditDateProblem.EARLIER -> other?.let(JamEditCopy::earlierThanUpcomingError)
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
        future.getOrNull(1)?.let { JamEditCopy.nextJamBecomes(it.date) } ?: JamEditCopy.NO_JAM_LEFT
    } else {
        JamEditCopy.nextJamStays(upcoming.date)
    }
    val statusLine = when (jam.status) {
        JamStatus.PUBLISHED -> JamEditCopy.PUBLISHED_LOST
        JamStatus.CREATED -> JamEditCopy.CREATED_LOST
        JamStatus.DRAFT -> null
    }
    return DeleteJamUiModel(
        title = JamEditCopy.deleteTitle(jam.date),
        jamDate = jam.date,
        lines = listOfNotNull(JamEditCopy.songsLost(songs), statusLine, nextLine, JamEditCopy.UNDO_WARNING),
        confirmLabel = JamEditCopy.DELETE,
        cancelLabel = PastJamsCopy.CANCEL,
        events = EventHandler(key = "${jam.date}|$songs") { event ->
            when (event) {
                DeleteJamUiModel.Event.Confirm -> editing.onConfirmDelete(jam.date, songs)
                DeleteJamUiModel.Event.Cancel -> editing.onCancelDelete()
            }
        },
    )
}
