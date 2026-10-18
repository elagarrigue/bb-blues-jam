package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.assistant.AssistantReply
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.AssistantHandlers
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.create.createForm
import com.bbbjam.feature.assistant.run.ApplyMode
import com.bbbjam.feature.assistant.run.ApplyPlan
import com.bbbjam.feature.assistant.run.ApplyRun
import com.bbbjam.feature.assistant.run.JamStep
import com.bbbjam.feature.assistant.run.ReplacePlan
import com.bbbjam.feature.assistant.run.applyPlan
import com.bbbjam.feature.assistant.run.applyPlanFor
import com.bbbjam.feature.assistant.run.replacePlan

/**
 * The targets block under a proposal (`assistant-proposal-targets`): Agregar, Reemplazar and Crear, in that
 * order, with the notes that say why one is missing or disabled. Crear is always offered once the snapshot
 * is read (C1); Agregar and Reemplazar need a readable upcoming list. After a create run that created the jam
 * but did not load everything, only the retry of the load for that jam's date is offered (C6).
 */
internal fun targets(
    turnId: Long,
    proposal: AssistantReply.Proposal,
    run: ApplyRun?,
    anyRunning: Boolean,
    inputs: ApplyInputs,
    handlers: AssistantHandlers,
): ApplyUiModel {
    val createdDate = run?.takeIf { it.mode == ApplyMode.CREATE && it.jam is JamStep.Created }?.jamDate
    return when {
        inputs.snapshot == null -> ApplyUiModel.None
        createdDate != null -> retryLoad(turnId, proposal, createdDate, anyRunning, inputs, handlers)
        else -> standardTargets(turnId, proposal, run, anyRunning, inputs, handlers)
    }
}

private fun standardTargets(
    turnId: Long,
    proposal: AssistantReply.Proposal,
    run: ApplyRun?,
    anyRunning: Boolean,
    inputs: ApplyInputs,
    handlers: AssistantHandlers,
): ApplyUiModel {
    val add = applyPlan(proposal.songs, inputs.snapshot)
    val replace = replacePlan(proposal.songs, inputs.snapshot)
    val unusable = (add as? ApplyPlan.Blocked)?.reason?.takeIf { it != ApplyPlan.Blocked.Reason.ALL_PRESENT }
    val create = TargetActionUiModel(
        target = ApplyMode.CREATE,
        label = UiText.res(R.string.assistant_create_action),
        enabled = !anyRunning,
        events = handlers.apply(turnId, ApplyMode.CREATE),
    )
    val actions = if (unusable != null) {
        listOf(create)
    } else {
        listOf(
            TargetActionUiModel(
                target = ApplyMode.ADD,
                label = UiText.res(
                    if (run?.mode ==
                        ApplyMode.ADD
                    ) {
                        R.string.assistant_apply_add_retry
                    } else {
                        R.string.assistant_apply_add
                    },
                ),
                enabled = add is ApplyPlan.Ready && !anyRunning,
                events = handlers.apply(turnId, ApplyMode.ADD),
            ),
            TargetActionUiModel(
                target = ApplyMode.REPLACE,
                label = UiText.res(
                    if (run?.mode ==
                        ApplyMode.REPLACE
                    ) {
                        R.string.assistant_replace_retry
                    } else {
                        R.string.assistant_replace_action
                    },
                ),
                enabled = replace is ReplacePlan.Ready && !anyRunning,
                events = handlers.apply(turnId, ApplyMode.REPLACE),
            ),
            create,
        )
    }
    return ApplyUiModel.Targets(
        actions = actions,
        notes = if (unusable != null) listOf(unusable.note()) else notes(add, replace),
        confirmation = confirmation(turnId, add, replace, inputs, handlers),
        createForm = createForm(turnId, proposal, inputs, handlers),
    )
}

// The create run made the jam but the load is incomplete: Reintentar los que faltan, for that jam (C6).
private fun retryLoad(
    turnId: Long,
    proposal: AssistantReply.Proposal,
    date: java.time.LocalDate,
    anyRunning: Boolean,
    inputs: ApplyInputs,
    handlers: AssistantHandlers,
): ApplyUiModel {
    val plan = applyPlanFor(proposal.songs, inputs.snapshot, date)
    val ready = plan as? ApplyPlan.Ready
    val retry = TargetActionUiModel(
        target = ApplyMode.ADD,
        label = UiText.res(R.string.assistant_apply_add_retry),
        enabled = !anyRunning,
        events = handlers.apply(turnId, ApplyMode.ADD),
    )
    return ApplyUiModel.Targets(
        actions = if (ready != null) listOf(retry) else emptyList(),
        notes = (plan as? ApplyPlan.Blocked)?.let { listOf(it.reason.note()) }.orEmpty(),
        confirmation = if (inputs.confirming == applyKey(turnId, ApplyMode.ADD)) {
            ready?.confirmation(handlers.confirmation(turnId))
        } else {
            null
        },
        createForm = null,
    )
}

private fun ApplyPlan.Blocked.Reason.note(): UiText = UiText.res(
    when (this) {
        ApplyPlan.Blocked.Reason.NO_UPCOMING -> R.string.assistant_create_note_no_upcoming
        ApplyPlan.Blocked.Reason.UNREADABLE -> R.string.assistant_apply_blocked_unreadable
        ApplyPlan.Blocked.Reason.ALL_PRESENT -> R.string.assistant_apply_blocked_all_present
    },
)

// An equal list says it all: "all present" would only repeat it.
private fun notes(add: ApplyPlan, replace: ReplacePlan): List<UiText> = buildList {
    val equal = (replace as? ReplacePlan.Blocked)?.reason == ReplacePlan.Blocked.Reason.ALREADY_EQUAL
    if (add is ApplyPlan.Blocked && !equal) add(UiText.res(R.string.assistant_apply_blocked_all_present))
    when ((replace as? ReplacePlan.Blocked)?.reason) {
        ReplacePlan.Blocked.Reason.ALREADY_EQUAL -> add(UiText.res(R.string.assistant_replace_note_equal))
        ReplacePlan.Blocked.Reason.REPEATED_IDS -> add(UiText.res(R.string.assistant_replace_note_repeated))
        else -> Unit
    }
}
