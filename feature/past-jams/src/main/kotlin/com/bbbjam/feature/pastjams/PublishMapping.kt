package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.setlist.JamEdit
import com.bbbjam.core.data.setlist.JamStatusChange
import com.bbbjam.core.data.setlist.SetlistPublish
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.presenter.EventHandler
import java.time.LocalDate

internal fun JamsAdminState.publishUiModel(jam: Jam): PublishUiModel? {
    val songCount = (jam.setlist as? Setlist.Available)?.songs?.size ?: 0
    val changing = statusChanges.any { it.jamDate == jam.date && it.state == JamStatusChange.State.Sending }
    val editing = edits.any { it.jamDate == jam.date && it.state == JamEdit.State.Sending }
    if (songCount == 0 || changing || editing) return null
    val entries = publishes.filter { it.jamDate == jam.date }
    val sending = entries.lastOrNull { it.state == SetlistPublish.State.Sending }
    val failed = entries.lastOrNull { it.state is SetlistPublish.State.Failed }
    return when {
        sending != null -> PublishUiModel.Publishing(PastJamsCopy.PUBLISHING)

        failed != null -> toPublishFailure(jam.date, failed)

        publish.confirming == jam.date.toString() -> confirmingPublish(jam.date, songCount)

        else -> PublishUiModel.Idle(
            PastJamsCopy.PUBLISH,
            EventHandler(key = jam.date) { publish.onRequest(jam.date) },
        )
    }
}

private fun JamsAdminState.toPublishFailure(date: LocalDate, failed: SetlistPublish): PublishUiModel.Failed {
    val reason = (failed.state as SetlistPublish.State.Failed).reason
    return PublishUiModel.Failed(
        id = failed.id,
        title = PastJamsCopy.PUBLISH_FAILED,
        message = publishFailureMessage(reason),
        consequence = PastJamsCopy.PUBLISH_CONSEQUENCE,
        retryLabel = PastJamsCopy.PUBLISH_RETRY,
        dismissLabel = PastJamsCopy.CLOSE,
        events = EventHandler(key = failed.id) { event ->
            when (event) {
                PublishUiModel.Event.Retry -> publish.onRetry(date, failed.id)
                PublishUiModel.Event.Dismiss -> onDismissPublish(failed.id)
                else -> Unit
            }
        },
    )
}

private fun JamsAdminState.confirmingPublish(date: LocalDate, songCount: Int): PublishUiModel.Confirming =
    PublishUiModel.Confirming(
        prompt = PastJamsCopy.PUBLISH_PROMPT,
        details = publishDetails(songCount),
        reversibleNote = PastJamsCopy.PUBLISH_REVERSIBLE,
        confirmLabel = PastJamsCopy.PUBLISH_CONFIRM,
        cancelLabel = PastJamsCopy.CANCEL,
        events = EventHandler(key = date) { event ->
            when (event) {
                PublishUiModel.Event.Confirm -> publish.onConfirm(date)
                PublishUiModel.Event.Cancel -> publish.onCancel(date)
                else -> Unit
            }
        },
    )
