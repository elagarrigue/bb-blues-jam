package com.bbbjam.core.ui.status

import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/**
 * The three single-select status chips of a jam card (`jam-status-chips`): [chips] in BORRADOR, CREADA,
 * PUBLICADA order and one handler. The selected chip is the jam's confirmed status; the presenter decides
 * what a tap on another chip does.
 */
data class JamStatusChipsUiModel(val chips: List<JamStatusChipUiModel>, val events: EventHandler<Event>) : UiModel {
    sealed interface Event : UiEvent {
        data class Select(val status: JamStatus) : Event
    }
}

/** One chip: [label] ("Creada"), the accessibility [description] ("Estado: Creada") and its state. */
data class JamStatusChipUiModel(
    val status: JamStatus,
    val label: String,
    val description: String,
    val selected: Boolean,
    val enabled: Boolean,
    val isPublished: Boolean,
    val pending: Boolean = false,
) : UiModel

/**
 * The chip whose status is being applied (`jam-status-chip-loading`): it is drawn with a pulsing outline and
 * announced with [description] ("Cambiando el estado a Creada…") instead of "Estado: Creada".
 */
data class PendingChip(val status: JamStatus, val description: String)

/**
 * The chips for a jam whose confirmed status is [current]; only the statuses in [enabled] can be tapped. The
 * [pending] chip, if any, is never enabled and carries its own description. Pure.
 */
fun jamStatusChips(
    current: JamStatus,
    enabled: Set<JamStatus>,
    events: EventHandler<JamStatusChipsUiModel.Event>,
    pending: PendingChip? = null,
): JamStatusChipsUiModel = JamStatusChipsUiModel(
    chips = listOf(JamStatus.DRAFT, JamStatus.CREATED, JamStatus.PUBLISHED).map { status ->
        val label = jamStatusBadge(status).label
        val isPending = pending?.status == status
        JamStatusChipUiModel(
            status = status,
            label = label,
            description = if (isPending) pending.description else "Estado: $label",
            selected = status == current,
            enabled = status in enabled && !isPending,
            isPublished = status == JamStatus.PUBLISHED,
            pending = isPending,
        )
    },
    events = events,
)
