package com.bbbjam.feature.pastjams.status

import com.bbbjam.core.data.setlist.jamedit.JamEdit
import com.bbbjam.core.data.setlist.jamstatus.JamStatusChange
import com.bbbjam.core.data.setlist.publish.SetlistPublish
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.pastjams.R
import com.bbbjam.feature.pastjams.admin.JamsAdminState
import com.bbbjam.feature.pastjams.admin.publishFailureMessage
import java.time.LocalDate

internal fun JamsAdminState.publishUiModel(jam: Jam): PublishUiModel? {
    val songCount = (jam.setlist as? Setlist.Available)?.songs?.size ?: 0
    val changing = statusChanges.any { it.jamDate == jam.date && it.state == JamStatusChange.State.Sending }
    val editing = edits.any { it.jamDate == jam.date && it.state == JamEdit.State.Sending }
    val entries = publishes.filter { it.jamDate == jam.date }
    val publishing = entries.any { it.state == SetlistPublish.State.Sending }
    val writing = changing || editing || publishing
    if (jam.status == JamStatus.PUBLISHED || songCount == 0 || writing) return null
    val failed = entries.lastOrNull { it.state is SetlistPublish.State.Failed }
    return when {
        failed != null -> toPublishFailure(jam.date, failed)
        publish.confirming == jam.date.toString() -> confirmingPublish(jam.date, songCount)
        else -> null
    }
}

private fun JamsAdminState.toPublishFailure(date: LocalDate, failed: SetlistPublish): PublishUiModel.Failed {
    val reason = (failed.state as SetlistPublish.State.Failed).reason
    return PublishUiModel.Failed(
        id = failed.id,
        title = UiText.res(R.string.past_jams_publish_failed),
        message = publishFailureMessage(reason),
        consequence = UiText.res(R.string.past_jams_publish_consequence),
        retryLabel = UiText.res(CoreR.string.list_state_retry),
        dismissLabel = UiText.res(R.string.past_jams_close),
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
        prompt = UiText.res(R.string.past_jams_publish_prompt),
        details = UiText.plural(R.plurals.past_jams_publish_details, songCount),
        reversibleNote = UiText.res(R.string.past_jams_publish_reversible),
        confirmLabel = UiText.res(R.string.past_jams_publish_confirm),
        cancelLabel = UiText.res(CoreR.string.jam_form_cancel),
        events = EventHandler(key = date) { event ->
            when (event) {
                PublishUiModel.Event.Confirm -> publish.onConfirm(date)
                PublishUiModel.Event.Cancel -> publish.onCancel(date)
                else -> Unit
            }
        },
    )
