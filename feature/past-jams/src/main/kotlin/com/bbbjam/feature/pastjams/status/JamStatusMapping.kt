package com.bbbjam.feature.pastjams.status

import com.bbbjam.core.data.setlist.jamedit.JamEdit
import com.bbbjam.core.data.setlist.jamstatus.JamStatusChange
import com.bbbjam.core.data.setlist.publish.SetlistPublish
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.status.JamStatusChipsUiModel
import com.bbbjam.core.ui.status.PendingChip
import com.bbbjam.core.ui.status.jamStatusBadge
import com.bbbjam.core.ui.status.jamStatusChips
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.pastjams.R
import com.bbbjam.feature.pastjams.admin.JamsAdminState
import com.bbbjam.feature.pastjams.admin.failureMessage

private fun actionLabel(target: JamStatus): UiText =
    UiText.res(if (target == JamStatus.DRAFT) R.string.past_jams_to_draft else R.string.past_jams_to_created)

/**
 * The status change layer for the future [jam] (`jam-status-chips`): the three chips with the confirmed status
 * selected (never anticipating the change), all drawn disabled while a change, a publication or an edit of
 * it is sending, the requested chip marked pending while a change or publication sends
 * (`jam-status-chip-loading`: no text line); PUBLICADA only when the card is the upcoming one, not yet
 * published, with a readable, non-empty list; the
 * publish confirmation in a modal dialog; a failed or outdated entry as a card.
 */
internal fun JamsAdminState.jamStatusUiModel(jam: Jam, isUpcoming: Boolean): JamStatusUiModel {
    val mine = statusChanges.filter { it.jamDate == jam.date }
    val sendingChange = mine.lastOrNull { it.state == JamStatusChange.State.Sending }
    val changeSending = sendingChange != null
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
            pending = pendingChip(sendingChange?.target, publishSending),
            events = chipEvents(jam, busy, canPublish, failed?.id),
        ),
        publishUnavailable = if (busy) null else publishUnavailable(jam, isUpcoming),
        confirming = if (confirmTarget == null || busy) null else confirmation(confirmTarget),
        failure = failed?.let { toFailure(it) },
        events = statusEvents(jam, failed?.id),
    )
}

/** The chip being applied: the requested status of a sending change, else Publicada for a sending publication. */
private fun pendingChip(changeTarget: JamStatus?, publishSending: Boolean): PendingChip? = when {
    changeTarget == JamStatus.PUBLISHED || (changeTarget == null && publishSending) ->
        PendingChip(JamStatus.PUBLISHED, UiText.res(R.string.past_jams_publishing_announcement))

    changeTarget != null -> PendingChip(
        changeTarget,
        UiText.res(R.string.past_jams_status_changing, jamStatusBadge(changeTarget).label),
    )

    else -> null
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
private fun JamsAdminState.publishUnavailable(jam: Jam, isUpcoming: Boolean): UiText? {
    val failedPublish = publishes.any { it.jamDate == jam.date && it.state is SetlistPublish.State.Failed }
    return when {
        jam.status == JamStatus.PUBLISHED || failedPublish -> null

        !isUpcoming -> UiText.res(R.string.past_jams_publish_only_upcoming)

        (jam.setlist as? Setlist.Available)?.songs?.isEmpty() == true ->
            UiText.res(R.string.past_jams_publish_needs_song)

        jam.setlist !is Setlist.Available -> UiText.res(R.string.past_jams_publish_needs_readable)

        else -> null
    }
}

/**
 * A tap on a chip. The selected chip does nothing. Publicada opens the publish confirmation only when enabled.
 * The confirmation is modal, so no other chip can be tapped while it is open. Nothing is sent while busy.
 */
private fun JamsAdminState.chipEvents(jam: Jam, busy: Boolean, canPublish: Boolean, failedId: Long?) =
    EventHandler<JamStatusChipsUiModel.Event>(
        key = "${jam.date}|${jam.status}|$busy|$canPublish|$failedId",
    ) { event ->
        val target = (event as JamStatusChipsUiModel.Event.Select).status
        val ignored = busy || target == jam.status
        when {
            ignored -> Unit
            target == JamStatus.PUBLISHED -> if (canPublish) publish.onRequest(jam.date)
            else -> status.onRequest(jam.date, jam.status, target)
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
    prompt = UiText.res(R.string.past_jams_unpublish_prompt),
    body = UiText.res(
        if (target == JamStatus.DRAFT) {
            R.string.past_jams_unpublish_to_draft_body
        } else {
            R.string.past_jams_unpublish_to_created_body
        },
    ),
    confirmLabel = actionLabel(target),
    cancelLabel = UiText.res(CoreR.string.jam_form_cancel),
)

private fun toFailure(entry: JamStatusChange): StatusFailureUiModel {
    val outdated = entry.state is JamStatusChange.State.Outdated
    val reason = (entry.state as? JamStatusChange.State.Failed)?.reason
    return StatusFailureUiModel(
        id = entry.id,
        title = UiText.res(R.string.past_jams_status_failed),
        message = if (reason == null) UiText.res(R.string.past_jams_status_script_outdated) else failureMessage(reason),
        consequence = UiText.res(R.string.past_jams_jam_unchanged),
        retryLabel = if (outdated) null else UiText.res(CoreR.string.list_state_retry),
        dismissLabel = UiText.res(R.string.past_jams_close),
    )
}
