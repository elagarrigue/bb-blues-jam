package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.feature.assistant.AssistantHandlers
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
        add(ApplyCopy.applyIntro(toAdd.size, jamDate))
        if (existingCount > 0) add(ApplyCopy.applyExisting(existingCount))
        if (skipped.isNotEmpty()) add(ApplyCopy.applySkipped(skipped.map { it.title }))
        if (status == JamStatus.PUBLISHED) add(ApplyCopy.APPLY_PUBLISHED)
    }
    return ConfirmationUiModel(
        title = ApplyCopy.APPLY_DIALOG_TITLE,
        lines = lines,
        confirmLabel = ApplyCopy.applyConfirm(toAdd.size),
        cancelLabel = ApplyCopy.APPLY_CANCEL,
        events = events,
    )
}

internal fun ReplacePlan.Ready.confirmation(events: EventHandler<ConfirmationUiModel.Event>): ConfirmationUiModel {
    val lines = buildList {
        add(ReplaceCopy.intro(target.size, jamDate))
        if (existingCount == 0) {
            add(ReplaceCopy.empty(toAdd.size))
        } else {
            if (toRemove.isNotEmpty()) {
                add(ReplaceCopy.removed(toRemove.map { it.title }))
                add(ReplaceCopy.lost(lostAssignments))
            }
            if (kept.isNotEmpty()) add(ReplaceCopy.kept(kept.size))
            if (toAdd.isNotEmpty()) add(ReplaceCopy.added(toAdd.size))
        }
        if (status == JamStatus.PUBLISHED) add(ReplaceCopy.PUBLISHED)
    }
    return ConfirmationUiModel(
        title = ReplaceCopy.DIALOG_TITLE,
        lines = lines,
        confirmLabel = ReplaceCopy.CONFIRM,
        cancelLabel = ApplyCopy.APPLY_CANCEL,
        events = events,
        destructive = toRemove.isNotEmpty(),
    )
}
