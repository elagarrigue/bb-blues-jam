package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.setlist.JamEdit
import com.bbbjam.core.data.setlist.JamStatusChange
import com.bbbjam.core.data.setlist.SetlistPublish
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.status.JamStatusChipsUiModel
import com.bbbjam.core.ui.status.jamStatusChips

private fun actionLabel(target: JamStatus): String =
    if (target == JamStatus.DRAFT) PastJamsCopy.TO_DRAFT else PastJamsCopy.TO_CREATED

/**
 * The status change layer for the future [jam] (`jam-status-chips`): the three chips with the confirmed status
 * selected (never anticipating the change), all drawn disabled while a change, a publication or an edit of
 * it is sending; PUBLICADA only when the card is the upcoming one, not yet published, with a readable,
 * non-empty list; the confirming dialog only while the jam is PUBLICADA; a failed or outdated entry as a card.
 */
internal fun JamsAdminState.jamStatusUiModel(jam: Jam, isUpcoming: Boolean): JamStatusUiModel {
    val mine = statusChanges.filter { it.jamDate == jam.date }
    val changeSending = mine.any { it.state == JamStatusChange.State.Sending }
    val publishSending = publishes.any { it.jamDate == jam.date && it.state == SetlistPublish.State.Sending }
    val confirmTarget = statusConfirmTarget(status.confirming, jam.date)
        ?.takeIf { jam.status == JamStatus.PUBLISHED && it != JamStatus.PUBLISHED }
    val failed = mine.lastOrNull { it.state !is JamStatusChange.State.Sending }
    val editSending = edits.any { it.jamDate == jam.date && it.state == JamEdit.State.Sending }
    val busy = changeSending || publishSending || editSending
    val canPublish = !busy && canPublish(jam, isUpcoming)
    return JamStatusUiModel(
        chips = jamStatusChips(
            current = jam.status,
            enabled = enabledChips(jam.status, busy, canPublish),
            events = chipEvents(jam, busy, canPublish, failed?.id),
        ),
        publishUnavailable = if (busy) null else publishUnavailable(jam, isUpcoming),
        confirming = if (confirmTarget == null || busy) null else confirmation(confirmTarget),
        sending = if (changeSending) PastJamsCopy.STATUS_SAVING else null,
        failure = failed?.let { toFailure(it) },
        events = statusEvents(jam, failed?.id),
    )
}

/**
 * The chips a tap can reach: none while [busy]; the selected one is enabled too while idle, so a screen reader does
 * not call the current status disabled (tapping it does nothing); Publicada only when it can be [canPublish]ed.
 */
private fun enabledChips(current: JamStatus, busy: Boolean, canPublish: Boolean): Set<JamStatus> = when {
    busy -> emptySet()
    canPublish || current == JamStatus.PUBLISHED -> JamStatus.entries.toSet()
    else -> setOf(JamStatus.DRAFT, JamStatus.CREATED)
}

/** Whether the Publicada chip can open the publish confirmation: the upcoming card, not published, a readable list. */
private fun JamsAdminState.canPublish(jam: Jam, isUpcoming: Boolean): Boolean {
    val songs = (jam.setlist as? Setlist.Available)?.songs?.size ?: 0
    val failedPublish = publishes.any { it.jamDate == jam.date && it.state is SetlistPublish.State.Failed }
    return isUpcoming && jam.status != JamStatus.PUBLISHED && songs > 0 && !failedPublish
}

/** Why Publicada is disabled on an idle card that is not published, in the planner's wording (P8). */
private fun JamsAdminState.publishUnavailable(jam: Jam, isUpcoming: Boolean): String? {
    val failedPublish = publishes.any { it.jamDate == jam.date && it.state is SetlistPublish.State.Failed }
    return when {
        jam.status == JamStatus.PUBLISHED || failedPublish -> null
        !isUpcoming -> PastJamsCopy.PUBLISH_ONLY_UPCOMING
        (jam.setlist as? Setlist.Available)?.songs?.isEmpty() == true -> PastJamsCopy.PUBLISH_NEEDS_SONG
        jam.setlist !is Setlist.Available -> PastJamsCopy.PUBLISH_NEEDS_READABLE
        else -> null
    }
}

/**
 * A tap on a chip. The selected chip does nothing. Publicada opens the publish confirmation (once) only when it
 * is enabled; any other chip first closes an open publish confirmation, then applies its normal rule. Nothing
 * is sent while the card is busy.
 */
private fun JamsAdminState.chipEvents(jam: Jam, busy: Boolean, canPublish: Boolean, failedId: Long?) =
    EventHandler<JamStatusChipsUiModel.Event>(
        key = "${jam.date}|${jam.status}|$busy|$canPublish|${publish.confirming}|$failedId",
    ) { event ->
        val target = (event as JamStatusChipsUiModel.Event.Select).status
        val ignored = busy || target == jam.status
        val confirmingPublish = publish.confirming == jam.date.toString()
        when {
            ignored -> Unit

            target == JamStatus.PUBLISHED -> if (canPublish && !confirmingPublish) publish.onRequest(jam.date)

            else -> {
                if (confirmingPublish) publish.onCancel(jam.date)
                status.onRequest(jam.date, jam.status, target)
            }
        }
    }

private fun JamsAdminState.statusEvents(jam: Jam, failedId: Long?) =
    EventHandler<JamStatusUiModel.Event>(key = "${jam.date}|${jam.status}|$failedId") { event ->
        when (event) {
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
