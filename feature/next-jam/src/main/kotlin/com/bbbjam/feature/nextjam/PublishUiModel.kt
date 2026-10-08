package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.setlist.SetlistPublish
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.presenter.EventHandler

internal fun AdminState.publishUiModel(jam: Jam): PublishUiModel? {
    val songCount = (jam.setlist as? Setlist.Available)?.songs?.size ?: 0
    if (songCount == 0) return null
    val entries = publishes.filter { it.jamDate == jam.date }
    val sending = entries.lastOrNull { it.state == SetlistPublish.State.Sending }
    val failed = entries.lastOrNull { it.state is SetlistPublish.State.Failed }
    return when {
        sending != null -> PublishUiModel.Publishing(NextJamCopy.PUBLISHING)

        failed != null -> toPublishFailure(jam.date, failed)

        publishConfirming == jam.date.toString() -> confirmingPublish(jam.date, songCount)

        else -> PublishUiModel.Idle(
            NextJamCopy.PUBLISH,
            EventHandler(key = jam.date) { onRequestPublish(jam.date) },
        )
    }
}

private fun AdminState.toPublishFailure(date: java.time.LocalDate, failed: SetlistPublish): PublishUiModel.Failed {
    val reason = (failed.state as SetlistPublish.State.Failed).reason
    return PublishUiModel.Failed(
        id = failed.id,
        title = NextJamCopy.PUBLISH_FAILED,
        message = publishFailureMessage(reason),
        consequence = NextJamCopy.PUBLISH_CONSEQUENCE,
        retryLabel = NextJamCopy.PUBLISH_RETRY,
        dismissLabel = NextJamCopy.CLOSE,
        events = EventHandler(key = failed.id) { event ->
            when (event) {
                PublishUiModel.Event.Retry -> onRetryPublish(date, failed.id)
                PublishUiModel.Event.Dismiss -> onDismiss(failed.id)
                else -> Unit
            }
        },
    )
}

private fun AdminState.confirmingPublish(date: java.time.LocalDate, songCount: Int): PublishUiModel.Confirming =
    PublishUiModel.Confirming(
        prompt = NextJamCopy.PUBLISH_PROMPT,
        details = publishDetails(songCount),
        irreversibleNote = NextJamCopy.PUBLISH_IRREVERSIBLE,
        confirmLabel = NextJamCopy.PUBLISH_CONFIRM,
        cancelLabel = NextJamCopy.CANCEL,
        events = EventHandler(key = date) { event ->
            when (event) {
                PublishUiModel.Event.Confirm -> onConfirmPublish(date)
                PublishUiModel.Event.Cancel -> onCancelPublish(date)
                else -> Unit
            }
        },
    )
