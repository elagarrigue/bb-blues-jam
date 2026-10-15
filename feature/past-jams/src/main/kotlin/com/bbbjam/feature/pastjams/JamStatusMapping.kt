package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.setlist.JamEdit
import com.bbbjam.core.data.setlist.JamStatusChange
import com.bbbjam.core.data.setlist.SetlistPublish
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.ui.presenter.EventHandler

/** The targets the admin can move [status] to without publishing, in display order. */
internal fun statusTargets(status: JamStatus): List<JamStatus> = when (status) {
    JamStatus.DRAFT -> listOf(JamStatus.CREATED)
    JamStatus.CREATED -> listOf(JamStatus.DRAFT)
    JamStatus.PUBLISHED -> listOf(JamStatus.CREATED, JamStatus.DRAFT)
}

private fun actionLabel(target: JamStatus): String =
    if (target == JamStatus.DRAFT) PastJamsCopy.TO_DRAFT else PastJamsCopy.TO_CREATED

/**
 * The status change layer for the upcoming [jam]: the actions, hidden while a change or a
 * publication of it is sending (one status line instead); the confirming dialog only while the jam
 * is PUBLICADA; a failed or outdated entry as a card. The badge never anticipates the change.
 */
internal fun JamsAdminState.jamStatusUiModel(jam: Jam): JamStatusUiModel {
    val mine = statusChanges.filter { it.jamDate == jam.date }
    val changeSending = mine.any { it.state == JamStatusChange.State.Sending }
    val publishSending = publishes.any { it.jamDate == jam.date && it.state == SetlistPublish.State.Sending }
    val confirmTarget = statusConfirmTarget(status.confirming, jam.date)
        ?.takeIf { jam.status == JamStatus.PUBLISHED && it != JamStatus.PUBLISHED }
    val failed = mine.lastOrNull { it.state !is JamStatusChange.State.Sending }
    val editSending = edits.any { it.jamDate == jam.date && it.state == JamEdit.State.Sending }
    val busy = changeSending || publishSending || editSending
    return JamStatusUiModel(
        actions = if (busy) {
            emptyList()
        } else {
            statusTargets(jam.status).map { StatusActionUiModel(actionLabel(it), it) }
        },
        confirming = if (confirmTarget == null || busy) null else confirmation(confirmTarget),
        sending = if (changeSending) PastJamsCopy.STATUS_SAVING else null,
        failure = failed?.let { toFailure(it) },
        events = statusEvents(jam, failed?.id),
    )
}

private fun JamsAdminState.statusEvents(jam: Jam, failedId: Long?) =
    EventHandler<JamStatusUiModel.Event>(key = "${jam.date}|${jam.status}|$failedId") { event ->
        when (event) {
            is JamStatusUiModel.Event.Request -> status.onRequest(jam.date, jam.status, event.target)

            JamStatusUiModel.Event.Confirm -> status.onConfirm(jam.date)

            JamStatusUiModel.Event.Cancel -> status.onCancel(jam.date)

            is JamStatusUiModel.Event.Retry ->
                statusChanges.firstOrNull { it.id == event.id }?.let { status.onRetry(jam.date, it.id, it.target) }

            is JamStatusUiModel.Event.Dismiss -> onDismissStatus(event.id)
        }
    }

private fun confirmation(target: JamStatus) = StatusConfirmUiModel(
    prompt = PastJamsCopy.UNPUBLISH_PROMPT,
    body = if (target ==
        JamStatus.DRAFT
    ) {
        PastJamsCopy.UNPUBLISH_TO_DRAFT_BODY
    } else {
        PastJamsCopy.UNPUBLISH_TO_CREATED_BODY
    },
    confirmLabel = actionLabel(target),
    cancelLabel = PastJamsCopy.CANCEL,
)

private fun toFailure(entry: JamStatusChange): StatusFailureUiModel {
    val outdated = entry.state is JamStatusChange.State.Outdated
    val reason = (entry.state as? JamStatusChange.State.Failed)?.reason
    return StatusFailureUiModel(
        id = entry.id,
        title = PastJamsCopy.STATUS_FAILED,
        message = if (reason == null) PastJamsCopy.STATUS_SCRIPT_OUTDATED else failureMessage(reason),
        consequence = PastJamsCopy.STATUS_CONSEQUENCE,
        retryLabel = if (outdated) null else PastJamsCopy.PUBLISH_RETRY,
        dismissLabel = PastJamsCopy.CLOSE,
    )
}
