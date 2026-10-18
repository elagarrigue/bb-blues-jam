package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.AssistantHandlers
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.run.ApplyMode
import com.bbbjam.feature.assistant.run.ApplyPlan
import com.bbbjam.feature.assistant.run.ReplacePlan

internal fun confirmation(
    turnId: Long,
    add: ApplyPlan,
    replace: ReplacePlan,
    inputs: ApplyInputs,
    handlers: AssistantHandlers,
): ConfirmationUiModel? {
    val events = handlers.confirmation(turnId)
    return when (inputs.confirming) {
        applyKey(turnId, ApplyMode.ADD) -> (add as? ApplyPlan.Ready)?.confirmation(events)
        applyKey(turnId, ApplyMode.REPLACE) -> (replace as? ReplacePlan.Ready)?.confirmation(events)
        else -> null
    }
}

internal fun ApplyPlan.Ready.confirmation(events: EventHandler<ConfirmationUiModel.Event>): ConfirmationUiModel {
    val lines = buildList {
        add(applyIntroText(toAdd.size, jamDate))
        if (existingCount > 0) add(applyExistingText(existingCount))
        if (skipped.isNotEmpty()) add(applySkippedText(skipped.map { it.title }))
        if (status == JamStatus.PUBLISHED) add(UiText.res(R.string.assistant_apply_add_published))
    }
    return ConfirmationUiModel(
        title = UiText.res(R.string.assistant_apply_add_dialog_title),
        lines = lines,
        confirmLabel = applyConfirmText(toAdd.size),
        cancelLabel = UiText.res(R.string.assistant_cancel),
        events = events,
    )
}

internal fun ReplacePlan.Ready.confirmation(events: EventHandler<ConfirmationUiModel.Event>): ConfirmationUiModel {
    val lines = buildList {
        add(replaceIntroText(target.size, jamDate))
        if (existingCount == 0) {
            add(replaceEmptyText(toAdd.size))
        } else {
            if (toRemove.isNotEmpty()) {
                add(replaceRemovedText(toRemove.map { it.title }))
                add(replaceLostText(lostAssignments))
            }
            if (kept.isNotEmpty()) add(replaceKeptText(kept.size))
            if (toAdd.isNotEmpty()) add(replaceAddedText(toAdd.size))
        }
        if (status == JamStatus.PUBLISHED) add(UiText.res(R.string.assistant_replace_published))
    }
    return ConfirmationUiModel(
        title = UiText.res(R.string.assistant_replace_dialog_title),
        lines = lines,
        confirmLabel = UiText.res(R.string.assistant_replace_confirm),
        cancelLabel = UiText.res(R.string.assistant_cancel),
        events = events,
        destructive = toRemove.isNotEmpty(),
    )
}
