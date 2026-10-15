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
) : UiModel

/** The chips for a jam whose confirmed status is [current]; only the statuses in [enabled] can be tapped. Pure. */
fun jamStatusChips(
    current: JamStatus,
    enabled: Set<JamStatus>,
    events: EventHandler<JamStatusChipsUiModel.Event>,
): JamStatusChipsUiModel = JamStatusChipsUiModel(
    chips = listOf(JamStatus.DRAFT, JamStatus.CREATED, JamStatus.PUBLISHED).map { status ->
        val label = jamStatusBadge(status).label
        JamStatusChipUiModel(
            status = status,
            label = label,
            description = "Estado: $label",
            selected = status == current,
            enabled = status in enabled,
            isPublished = status == JamStatus.PUBLISHED,
        )
    },
    events = events,
)
