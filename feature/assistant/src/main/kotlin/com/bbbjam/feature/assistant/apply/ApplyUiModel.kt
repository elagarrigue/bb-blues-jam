package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.ui.jamform.JamFormUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.run.ApplyMode

/** What sits under a proposal to use it on the upcoming jam (`assistant-proposal-targets`). */
internal sealed interface ApplyUiModel : UiModel {
    /** Nothing: not the latest proposal, snapshot not read yet, a pending turn, or a finished success. */
    data object None : ApplyUiModel

    /**
     * The targets block: one button per [actions] (Agregar, Reemplazar), the [notes] saying why one is
     * missing or disabled, and the open [confirmation]. Tapping a button only opens its confirmation;
     * nothing is written until the dialog's confirm (D-24).
     */
    data class Targets(
        val actions: List<TargetActionUiModel>,
        val notes: List<UiText>,
        val confirmation: ConfirmationUiModel?,
        /** The open "Crear una jam nueva con esta lista" form: the shared jam form, its submit is the confirmation. */
        val createForm: JamFormUiModel? = null,
    ) : ApplyUiModel
}

/** One target button: [target] says where the songs go. */
internal data class TargetActionUiModel(
    val target: ApplyMode,
    val label: UiText,
    val enabled: Boolean,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Request : Event
    }
}

/** The confirmation dialog: drawn while this is non-null. */
internal data class ConfirmationUiModel(
    val title: UiText,
    val lines: List<UiText>,
    val confirmLabel: UiText,
    val cancelLabel: UiText,
    val events: EventHandler<Event>,
    /** True when confirming removes something: the confirm action is drawn in `error`. */
    val destructive: Boolean = false,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Confirm : Event

        data object Cancel : Event
    }
}

internal enum class ApplyTone { NEUTRAL, SUCCESS, FAILURE, MUTED }

/** The run's one-line summary (a polite live region) and, while it runs, the loading dots and "Detener". */
internal data class ApplySummaryUiModel(
    val text: UiText,
    val tone: ApplyTone,
    val stopLabel: UiText?,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Stop : Event
    }
}

internal enum class ApplyStatusKind { PROGRESS, DONE, SKIPPED, FAILED, NOT_ATTEMPTED }

/** The caption under a proposed song while or after a run. */
internal data class ApplyStatusUiModel(val text: UiText, val kind: ApplyStatusKind) : UiModel

/** A removed song's line under a replace: its [title] and what happened to it ([text]). */
internal data class ApplyStatusLineUiModel(val title: String, val text: UiText, val kind: ApplyStatusKind) : UiModel
