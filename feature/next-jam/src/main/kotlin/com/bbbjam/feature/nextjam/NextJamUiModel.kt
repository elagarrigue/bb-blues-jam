package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.filter.InstrumentFilterBarUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.ListErrorUiModel
import com.bbbjam.core.ui.state.PullRefreshUiModel
import com.bbbjam.core.ui.state.StalenessNoticeUiModel
import com.bbbjam.core.ui.status.JamStatusBadgeUiModel
import com.bbbjam.core.ui.strip.InstrumentChipUiModel

/**
 * Everything Próxima jam draws, as plain values. A row opens the song detail
 * (`song-detail-replaces-row-expansion`; rows no longer expand) and rows can be filtered by
 * instrument (`instrument-filter-chips`). Every non-happy path is its own state
 * (`list-states`): skeleton rows while nothing was read, an error block when nothing is cached and
 * the read failed, an empty block when there is nothing to list, and a staleness notice above
 * cached data whose latest refresh failed. Every state can be pulled to refresh
 * (`live-refresh-during-jam`): [pullRefresh] is the indicator and the pull's event.
 */
sealed interface NextJamUiModel : UiModel {
    val pullRefresh: PullRefreshUiModel

    /**
     * Nothing to show yet: no emission, or nothing was ever fetched and a read is running (or none
     * has failed yet). Drawn as skeleton rows; [description] is what a screen reader says for them.
     */
    data class Loading(
        val description: String,
        override val pullRefresh: PullRefreshUiModel = PullRefreshUiModel.IDLE,
    ) : NextJamUiModel

    /** Nothing was ever fetched and the latest read failed: the error block with a retry button. */
    data class Failed(
        val error: ListErrorUiModel,
        override val pullRefresh: PullRefreshUiModel = PullRefreshUiModel.IDLE,
    ) : NextJamUiModel

    /**
     * The jams were read and none is upcoming. [staleness] is set when the latest refresh failed.
     * [adminHint] is the admin's line on how to create a jam (J1), null for musicians: adding never
     * creates a jam, so there is no add button here.
     */
    data class NoUpcomingJam(
        val empty: EmptyStateUiModel,
        val staleness: StalenessNoticeUiModel?,
        val adminHint: String? = null,
        override val pullRefresh: PullRefreshUiModel = PullRefreshUiModel.IDLE,
    ) : NextJamUiModel

    /**
     * [staleness] is set exactly when something is cached and the latest refresh failed. [admin] is
     * the admin's controls (`admin-add-song-to-setlist`), null for musicians, so a musician's model
     * is exactly what it was before admin controls existed.
     */
    data class Jam(
        val header: JamHeaderUiModel,
        val setlist: SetlistUiModel,
        val staleness: StalenessNoticeUiModel?,
        val admin: NextJamAdminUiModel? = null,
        override val pullRefresh: PullRefreshUiModel = PullRefreshUiModel.IDLE,
    ) : NextJamUiModel
}

/**
 * The admin's layer on Próxima jam (D-15: a state of the same screen). Everything here is drawn
 * after what a musician sees, so nothing above it moves (DESIGN.md "Admin controls"): publication
 * status under the header, then pending/failed row mutations and [addSong] after the rows. The
 * filter never hides pending rows or failures.
 */
data class NextJamAdminUiModel(
    val status: AdminStatusUiModel,
    val pending: List<PendingRowUiModel>,
    val failures: List<AddFailureUiModel>,
    val addSong: AddSongActionUiModel?,
) : UiModel

/**
 * The upcoming jam's status as Próxima jam shows it to the admin (read-only since `admin-jams-tab`,
 * D-27): the shared [badge], the [note] of what musicians see and the [hint] that points to the Jams
 * tab, where the status is changed and the list is published.
 */
data class AdminStatusUiModel(val badge: JamStatusBadgeUiModel, val note: String, val hint: String) : UiModel

/** An add in flight: [title] and [status] ("Agregando…"), at the end of the list. */
data class PendingRowUiModel(val id: Long, val title: String, val status: String) : UiModel

/**
 * A failed add, remove or key change (`admin-remove-song-from-setlist` and `admin-set-key` reuse the
 * shape; the name stays to avoid churn), kept until the admin closes it: [title] ("No se pudo
 * agregar «Crossroads»", "No se pudo quitar «Crossroads»" or "No se pudo cambiar la tonalidad de
 * «Crossroads»"), [message] by outcome (C1) and [dismissLabel] ("Cerrar").
 */
data class AddFailureUiModel(
    val id: Long,
    val title: String,
    val message: String,
    val dismissLabel: String,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        /** Remove this card. Removes only the in-memory entry; nothing is written. */
        data object Dismiss : Event
    }
}

/** "Agregar tema": opens the catalog picker for the jam. Navigation only. */
data class AddSongActionUiModel(val label: String, val events: EventHandler<Event>) : UiModel {
    sealed interface Event : UiEvent {
        data object Open : Event
    }
}

/** [date] is "Sábado 31 de octubre · 21:00"; [venue] is the Sheet's `lugar` as written. */
data class JamHeaderUiModel(val date: String, val venue: String, val timeRemaining: String) : UiModel

sealed interface SetlistUiModel : UiModel {
    /**
     * The rows in position order, only those the instrument filter shows; [droppedRowsNote] says how
     * many rows could not be read, or is null. [filterBar] is the instrument filter
     * (`instrument-filter-chips`), null when the setlist has no song (the empty case is not a
     * filter's no-results case).
     */
    data class Songs(
        val rows: List<SongRowUiModel>,
        val droppedRowsNote: String?,
        val filterBar: InstrumentFilterBarUiModel?,
    ) : SetlistUiModel

    /** A published setlist with no song: the empty block, and no filter bar. */
    data class Empty(val empty: EmptyStateUiModel) : SetlistUiModel

    /**
     * The jam is a draft (`unpublished-setlist-state`): the draft card instead of rows, decided from
     * the jam's status, so a draft never shows a song even when its setlist reached the app.
     */
    data class Withheld(val draft: DraftSetlistUiModel) : SetlistUiModel

    /** A published setlist that could not be read: one muted line instead of rows. */
    data class Unavailable(val message: String) : SetlistUiModel
}

/**
 * The draft card: [label] is the badge (drawn uppercase, "EN PREPARACIÓN"), [title] the heading and
 * [message] what the musician will see once the list is published. Nothing in it is clickable.
 */
data class DraftSetlistUiModel(val label: String, val title: String, val message: String) : UiModel

/**
 * One song row, always collapsed (`song-detail-replaces-row-expansion`; D-07). [position] is the
 * Sheet's `posicion`, never renumbered by the read path (a removal renumbers the Sheet itself,
 * `admin-remove-song-from-setlist`); [positionLabel] is it zero-padded ("01"). [keyDescription] is
 * what a screen reader says for [key]. [instruments] is the instrument strip (slots in Sheet column
 * order, then the extra participants). Tapping the header opens the song detail
 * ([Event.OpenDetail]); [openDetailLabel] ("ver el detalle del tema") is that action for screen
 * readers. [admin] is the admin's part of the row, the compact bar under the header; null for
 * musicians, so a musician's row is exactly what it was before admin controls existed.
 */
data class SongRowUiModel(
    val position: Int,
    val positionLabel: String,
    val rowKey: String = "p$position",
    val title: String,
    val key: String,
    val keyDescription: String,
    val instruments: List<InstrumentChipUiModel>,
    val openDetailLabel: String,
    val events: EventHandler<Event>,
    val admin: SongRowAdminUiModel? = null,
) : UiModel {
    sealed interface Event : UiEvent {
        /** Open this song's detail. Navigation only, never a write. */
        data object OpenDetail : Event
    }
}

/**
 * The admin's part of one row (D-15): the list-level actions only, drawn as a compact bar under the
 * header (`song-detail-replaces-row-expansion`, P3). [move] ("Subir"/"Bajar", `admin-reorder-songs`;
 * null while the removal is sent) and [removal] (the remove action, its inline confirmation, or the
 * status while the removal is sent, `admin-remove-song-from-setlist`, U1), which replaces the bar
 * while it confirms. [saveStatus] is "Guardando…" while a write of the song is sending (its key,
 * lineup, slots, extras or position), else null; it is drawn under the title line. Song-level
 * controls live in the song detail.
 */
data class SongRowAdminUiModel(
    val removal: RemovalUiModel,
    val move: MoveUiModel? = null,
    val saveStatus: String? = null,
) : UiModel

/**
 * The admin's "Otros" editor (`admin-edit-extra-participants`): the "+ Otro" row at the end of the
 * open slots ([rowLabel] "Otro", drawn as "+ Otro"; its button [actionLabel] "Agregar" with
 * [actionDescription] "Agregar a Otros" for screen readers), and the dialog it opens while
 * [formVisible]: two fields, "Agregar" (enabled only when [submitEnabled]: both trimmed values
 * non-blank) and "Cancelar". The extras themselves, each with its "Quitar", are lines of the song detail's
 * lineup panel (`ui-polish-pass`, U4), which sends [Event.Remove] here.
 */
data class ExtraParticipantEditorUiModel(
    val formVisible: Boolean,
    val name: String,
    val instrument: String,
    val submitEnabled: Boolean,
    val saving: Boolean,
    val rowLabel: String,
    val actionLabel: String,
    val actionDescription: String,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Open : Event
        data class NameChanged(val value: String) : Event
        data class InstrumentChanged(val value: String) : Event
        data object Submit : Event
        data object Cancel : Event
        data class Remove(val ordinal: Int, val name: String, val instrument: String) : Event
    }
}

/** The move actions of an admin row. Edges count the full setlist, not filtered rows. */
data class MoveUiModel(val up: MoveActionUiModel, val down: MoveActionUiModel) : UiModel

/** One visible one-place reorder action. Disabled actions remain drawn to keep the layout stable. */
data class MoveActionUiModel(
    val label: String,
    val clickLabel: String,
    val enabled: Boolean,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Move : Event
    }
}

/** "Cambiar tonalidad": opens the key picker for the detail's song. Navigation only. */
data class SetKeyActionUiModel(val label: String, val events: EventHandler<Event>) : UiModel {
    sealed interface Event : UiEvent {
        data object Open : Event
    }
}

/**
 * Removing one song from the setlist, in three steps. Nothing is written until [Confirming]'s
 * [RemovalUiModel.Event.Confirm]; there is no undo, and the row disappears only when the server
 * confirms (no optimistic hide).
 */
sealed interface RemovalUiModel : UiModel {
    sealed interface Event : UiEvent {
        /** Show the confirmation instead of the action. Changes nothing anywhere. */
        data object RequestRemove : Event

        /** Send the removal. Acts only while this row is still confirming, so a double tap removes once. */
        data object Confirm : Event

        /** Back to the action. Changes nothing anywhere. */
        data object Cancel : Event
    }

    /** "Quitar de la lista". */
    data class Idle(val label: String, val events: EventHandler<Event>) : RemovalUiModel

    /**
     * [prompt] ("¿Quitar «Crossroads» de la lista?"), then [details]: how many assigned musicians go
     * with the row, and whether the list is published (each line only when it applies), then
     * [confirmLabel] ("Quitar") and [cancelLabel] ("Cancelar").
     */
    data class Confirming(
        val prompt: String,
        val details: List<String>,
        val confirmLabel: String,
        val cancelLabel: String,
        val events: EventHandler<Event>,
    ) : RemovalUiModel

    /** "Quitando…" while the removal is sent; no control. */
    data class Removing(val status: String) : RemovalUiModel
}

/** The song detail's always-visible admin lineup controls. */
data class LineupEditorUiModel(val heading: String, val lines: List<LineupEditorLineUiModel>) : UiModel

data class LineupEditorLineUiModel(
    val instrument: com.bbbjam.core.model.Instrument,
    val countLabel: String,
    val removeDescription: String,
    val addDescription: String,
    val canRemove: Boolean,
    val canAdd: Boolean,
    val blockedNote: String?,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Remove : Event
        data object Add : Event
    }
}
