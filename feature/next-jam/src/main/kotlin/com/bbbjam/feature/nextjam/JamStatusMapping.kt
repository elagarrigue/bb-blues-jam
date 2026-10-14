package com.bbbjam.feature.nextjam

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
    if (target == JamStatus.DRAFT) NextJamCopy.TO_DRAFT else NextJamCopy.TO_CREATED

/**
 * The status change layer for the upcoming [jam]: the actions, hidden while a change or a
 * publication of it is sending (one status line instead); the confirming dialog only while the jam
 * is PUBLICADA; a failed or outdated entry as a card. The badge never anticipates the change.
 */
internal fun AdminState.jamStatusUiModel(jam: Jam): JamStatusUiModel {
    val mine = statusChanges.filter { it.jamDate == jam.date }
    val changeSending = mine.any { it.state == JamStatusChange.State.Sending }
    val publishSending = publishes.any { it.jamDate == jam.date && it.state == SetlistPublish.State.Sending }
    val confirmTarget = statusConfirmTarget(statusConfirming, jam.date)
        ?.takeIf { jam.status == JamStatus.PUBLISHED && it != JamStatus.PUBLISHED }
    val failed = mine.lastOrNull { it.state !is JamStatusChange.State.Sending }
    val busy = changeSending || publishSending
    return JamStatusUiModel(
        actions = if (busy) {
            emptyList()
        } else {
            statusTargets(jam.status).map { StatusActionUiModel(actionLabel(it), it) }
        },
        confirming = if (confirmTarget == null || busy) null else confirmation(confirmTarget),
        sending = if (changeSending) NextJamCopy.STATUS_SAVING else null,
        failure = failed?.let { toFailure(it) },
        events = statusEvents(jam, failed?.id),
    )
}

private fun AdminState.statusEvents(jam: Jam, failedId: Long?) =
    EventHandler<JamStatusUiModel.Event>(key = "${jam.date}|${jam.status}|$failedId") { event ->
        when (event) {
            is JamStatusUiModel.Event.Request -> onStatusRequest(jam.date, jam.status, event.target)

            JamStatusUiModel.Event.Confirm -> onStatusConfirm(jam.date)

            JamStatusUiModel.Event.Cancel -> onStatusCancel(jam.date)

            is JamStatusUiModel.Event.Retry ->
                statusChanges.firstOrNull { it.id == event.id }?.let { onStatusRetry(jam.date, it.id, it.target) }

            is JamStatusUiModel.Event.Dismiss -> onDismiss(event.id)
        }
    }

private fun confirmation(target: JamStatus) = StatusConfirmUiModel(
    prompt = NextJamCopy.UNPUBLISH_PROMPT,
    body = if (target ==
        JamStatus.DRAFT
    ) {
        NextJamCopy.UNPUBLISH_TO_DRAFT_BODY
    } else {
        NextJamCopy.UNPUBLISH_TO_CREATED_BODY
    },
    confirmLabel = actionLabel(target),
    cancelLabel = NextJamCopy.CANCEL,
)

private fun toFailure(entry: JamStatusChange): StatusFailureUiModel {
    val outdated = entry.state is JamStatusChange.State.Outdated
    val reason = (entry.state as? JamStatusChange.State.Failed)?.reason
    return StatusFailureUiModel(
        id = entry.id,
        title = NextJamCopy.STATUS_FAILED,
        message = if (reason == null) NextJamCopy.STATUS_SCRIPT_OUTDATED else failureMessage(reason),
        consequence = NextJamCopy.STATUS_CONSEQUENCE,
        retryLabel = if (outdated) null else NextJamCopy.PUBLISH_RETRY,
        dismissLabel = NextJamCopy.CLOSE,
    )
}
