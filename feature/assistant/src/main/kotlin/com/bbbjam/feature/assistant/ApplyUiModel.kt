package com.bbbjam.feature.assistant

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/** What sits under a proposal to load it into the upcoming jam (`assistant-apply-proposal`). */
sealed interface ApplyUiModel : UiModel {
    /** Nothing: not the latest proposal, snapshot not read yet, a pending turn, or a finished success. */
    data object None : ApplyUiModel

    /** The list cannot be loaded now; [note] says why. */
    data class Blocked(val note: String) : ApplyUiModel

    /**
     * The "Usar esta lista" (or "Reintentar los que faltan") button. Tapping it only opens
     * [confirmation]; nothing is written until the dialog's confirm (D-24).
     */
    data class Action(
        val label: String,
        val enabled: Boolean,
        val events: EventHandler<Event>,
        val confirmation: ConfirmationUiModel?,
    ) : ApplyUiModel {
        sealed interface Event : UiEvent {
            data object Request : Event
        }
    }
}

/** The confirmation dialog: drawn while this is non-null. */
data class ConfirmationUiModel(
    val title: String,
    val lines: List<String>,
    val confirmLabel: String,
    val cancelLabel: String,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Confirm : Event

        data object Cancel : Event
    }
}

enum class ApplyTone { NEUTRAL, SUCCESS, FAILURE, MUTED }

/** The run's one-line summary (a polite live region) and, while it runs, "Detener". */
data class ApplySummaryUiModel(
    val text: String,
    val tone: ApplyTone,
    val stopLabel: String?,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Stop : Event
    }
}

enum class ApplyStatusKind { PROGRESS, DONE, SKIPPED, FAILED, NOT_ATTEMPTED }

/** The caption under a proposed song while or after a run. */
data class ApplyStatusUiModel(val text: String, val kind: ApplyStatusKind) : UiModel
