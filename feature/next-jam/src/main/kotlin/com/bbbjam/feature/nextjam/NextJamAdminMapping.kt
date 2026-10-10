package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.setlist.Assignment
import com.bbbjam.core.data.setlist.ExtraParticipantChange
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.data.setlist.LineupChange
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistMove
import com.bbbjam.core.data.setlist.SetlistPublish
import com.bbbjam.core.data.setlist.SetlistRemove
import com.bbbjam.core.data.setlist.SlotClear
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam as DomainJam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.strip.toInstrumentChips
import com.bbbjam.core.ui.text.SongListText
import java.time.LocalDate

/*
 * The admin's part of the Próxima jam mapping, split out of `NextJamPresenter.kt` unchanged
 * (`project-hygiene-pass`): the admin state the presenter builds, the admin block under the header
 * and after the rows, and the admin decoration of each row.
 */

/** The admin's row state for one jam: the admin state and the jam (its date and whether it is published). */
internal class RowAdmin(val state: AdminState, val jam: DomainJam) {
    val removal: RemovalState
        get() = state.removal
}

/**
 * What the presenter knows only for an admin: the adds of `SetlistRepository.observeAdds()`, the
 * admin callbacks, the [removal] state and the [keyChanges] of `observeKeyChanges()`. Null for
 * musicians.
 */
internal class AdminState(
    val adds: List<SetlistAdd>,
    val onAddSong: (LocalDate) -> Unit,
    val onDismiss: (Long) -> Unit,
    val removal: RemovalState = RemovalState(),
    val keyChanges: List<KeyChange> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val extraParticipantChanges: List<ExtraParticipantChange> = emptyList(),
    val slotClears: List<SlotClear> = emptyList(),
    val moves: List<SetlistMove> = emptyList(),
    val onSetKey: (LocalDate, SongId) -> Unit = { _, _ -> },
    val extraFormKey: String? = null,
    val extraName: String = "",
    val extraInstrument: String = "",
    val onExtraEvent: (LocalDate, SongId, ExtraParticipantEditorUiModel.Event) -> Unit = { _, _, _ -> },
    val lineupChanges: List<LineupChange> = emptyList(),
    val lineupEditing: String? = null,
    val reservations: LineupReservations = LineupReservations(),
    val onLineupEditor: (LocalDate, SongId, LineupEditorUiModel.Event) -> Unit = { _, _, _ -> },
    val onLineupCount: (LocalDate, SongId, Instrument, LineupEditorLineUiModel.Event) -> Unit = { _, _, _, _ -> },
    val onAssignSlot: (LocalDate, SongId, Instrument, SlotPosition) -> Unit = { _, _, _, _ -> },
    val onClearSlot: (LocalDate, SongId, Instrument, SlotPosition, String) -> Unit = { _, _, _, _, _ -> },
    val onMove: (LocalDate, SongId, Int) -> Unit = { _, _, _ -> },
    val publishes: List<SetlistPublish> = emptyList(),
    val publishConfirming: String? = null,
    val onRequestPublish: (LocalDate) -> Unit = {},
    val onCancelPublish: (LocalDate) -> Unit = {},
    val onConfirmPublish: (LocalDate) -> Unit = {},
    val onRetryPublish: (LocalDate, Long) -> Unit = { _, _ -> },
)

/**
 * The removals of `SetlistRepository.observeRemoves()`, the row that is [confirming] (its
 * [removalKey], or null) and the one handler every row's removal events go to.
 */
internal class RemovalState(
    val removes: List<SetlistRemove> = emptyList(),
    val confirming: String? = null,
    val onEvent: (LocalDate, SongId, RemovalUiModel.Event) -> Unit = { _, _, _ -> },
)

/** The confirming row's saveable key: bundle-safe, unique per jam and song. */
internal fun removalKey(date: LocalDate, songId: SongId): String = "$date|${songId.value}"

/**
 * The admin layer for [jam]: the adds for its date only, sending ones as pending rows and failed
 * ones as cards, and "Agregar tema" only while the setlist is readable. Handlers are keyed by what
 * they act on, so a model for another jam or entry never keeps an earlier handler.
 */
internal fun AdminState.toUiModel(jam: DomainJam): NextJamAdminUiModel {
    val mine = adds.filter { it.jamDate == jam.date }
    val isDraft = jam.status == JamStatus.DRAFT
    return NextJamAdminUiModel(
        status = AdminStatusUiModel(
            badge = if (isDraft) NextJamCopy.DRAFT_BADGE else NextJamCopy.PUBLISHED_BADGE,
            isPublished = !isDraft,
            note = if (isDraft) NextJamCopy.DRAFT_NOTE else NextJamCopy.PUBLISHED_NOTE,
            publish = if (isDraft) publishUiModel(jam) else null,
        ),
        pending = mine.filter { it.state == SetlistAdd.State.Sending }
            .map { PendingRowUiModel(it.id, it.title, NextJamCopy.ADDING) },
        failures = failures(jam.date),
        addSong = if (jam.setlist is Setlist.Available) {
            AddSongActionUiModel(NextJamCopy.ADD_SONG, EventHandler(key = jam.date) { onAddSong(jam.date) })
        } else {
            null
        },
    )
}

/**
 * The failed adds, removals and key changes of [date], as cards in id order (call order). A key
 * change has its own message mapping ([keyFailureMessage]).
 */
private fun AdminState.failures(date: LocalDate): List<AddFailureUiModel> {
    val failedAdds = adds.filter { it.jamDate == date }.mapNotNull { add ->
        (add.state as? SetlistAdd.State.Failed)?.let {
            Triple(add.id, NextJamCopy.addFailed(add.title), failureMessage(it.reason))
        }
    }
    val failedRemoves = removal.removes.filter { it.jamDate == date }.mapNotNull { remove ->
        (remove.state as? SetlistRemove.State.Failed)?.let {
            Triple(remove.id, NextJamCopy.removeFailed(remove.title), failureMessage(it.reason))
        }
    }
    val failedKeys = keyChanges.filter { it.jamDate == date }.mapNotNull { change ->
        (change.state as? KeyChange.State.Failed)?.let {
            Triple(change.id, NextJamCopy.keyFailed(change.title), keyFailureMessage(it.reason))
        }
    }
    val failedLineups = lineupChanges.filter { it.jamDate == date }.mapNotNull { change ->
        (change.state as? LineupChange.State.Failed)?.let {
            Triple(change.id, NextJamCopy.lineupFailed(change.title), lineupFailureMessage(it.reason))
        }
    }
    val failedAssignments = assignments.filter { it.jamDate == date }.mapNotNull { assignment ->
        (assignment.state as? Assignment.State.Failed)?.let {
            Triple(
                assignment.id,
                NextJamCopy.assignmentFailed(assignment.musicianName.value, assignment.title),
                assignmentFailureMessage(it.reason),
            )
        }
    }
    val failedSlotClears = slotClears.filter { it.jamDate == date }.mapNotNull { clear ->
        (clear.state as? SlotClear.State.Failed)?.let {
            Triple(clear.id, NextJamCopy.CLEAR_FAILED, slotClearFailureMessage(it.reason))
        }
    }
    val failedMoves = moves.filter { it.jamDate == date }.mapNotNull { move ->
        (move.state as? SetlistMove.State.Failed)?.let {
            Triple(move.id, MoveCopy.failed(move.title), moveFailureMessage(it.reason))
        }
    }
    val failedExtras = extraParticipantChanges.filter { it.jamDate == date }.mapNotNull { change ->
        (change.state as? ExtraParticipantChange.State.Failed)?.let {
            Triple(change.id, NextJamCopy.EXTRA_FAILED, failureMessage(it.reason))
        }
    }
    val allFailures =
        failedAdds + failedRemoves + failedKeys + failedLineups + failedAssignments + failedSlotClears + failedMoves +
            failedExtras
    return allFailures.sortedBy { it.first }.map { (id, title, message) ->
        AddFailureUiModel(
            id = id,
            title = title,
            message = message,
            dismissLabel = NextJamCopy.CLOSE,
            events = EventHandler(key = id) { onDismiss(id) },
        )
    }
}

/**
 * The admin's [row] of [song]: the latest pending key change drawn in place of the cached key, with
 * "Guardando…" (O1; a failed change never overlays, which is the revert), and the admin's part.
 * "Cambiar tonalidad" is keyed by (date, songId), as the removal's handlers.
 */
internal fun RowAdmin.decorate(row: SongRowUiModel, song: JamSong, total: Int): SongRowUiModel {
    val pending = state.keyChanges.pendingKey(jam.date, song.songId)
    val setKey = SetKeyActionUiModel(
        NextJamCopy.SET_KEY,
        EventHandler(key = "${removalKey(jam.date, song.songId)}|setKey") { state.onSetKey(jam.date, song.songId) },
    )
    val removal = removalModel(song)
    val sendingMove = isMoveSending(song)
    val move = moveModel(row, song, total, removal)
    val extraEditor = state.extraEditor(jam.date, song)
    val shownExtras = extraEditor.extras
    val savingExtras = state.extraParticipantChanges.any {
        it.jamDate == jam.date && it.songId == song.songId && it.state == ExtraParticipantChange.State.Sending
    }
    val admin = SongRowAdminUiModel(
        setKey = setKey,
        removal = removal,
        move = move,
        lineup = state.lineupEditor(jam.date, song),
        saveStatus = if (listOf(
                pending != null,
                sendingMove,
                state.isSavingLineup(jam.date, song.songId),
                state.isSavingAssignment(jam.date, song.songId),
                state.slotClears.any {
                    it.jamDate == jam.date && it.songId == song.songId && it.state == SlotClear.State.Sending
                },
                savingExtras,
            ).any { it }
        ) {
            NextJamCopy.SAVING
        } else {
            null
        },
    )
    val lineup = state.lineupPanel(jam.date, song, shownExtras)
    return row.copy(
        key = pending?.value ?: row.key,
        keyDescription = if (pending == null) row.keyDescription else SongListText.keyDescription(pending.value),
        instruments = song.lineup.toInstrumentChips(shownExtras),
        lineup = lineup,
        admin = admin.copy(extraParticipants = extraEditor),
    )
}

/**
 * The removal part of [song]'s row: "Quitando…" while a removal of it is sending, the confirmation
 * while it is the confirming row, else the action. Handlers are keyed by (date, songId, step), so a
 * model for another song never keeps an earlier handler.
 */
private fun RowAdmin.removalModel(song: JamSong): RemovalUiModel {
    val key = removalKey(jam.date, song.songId)
    val sending = removal.removes.any {
        it.jamDate == jam.date && it.songId == song.songId && it.state == SetlistRemove.State.Sending
    }
    val handler: (String) -> EventHandler<RemovalUiModel.Event> = { step ->
        EventHandler(key = "$key|$step") { event -> removal.onEvent(jam.date, song.songId, event) }
    }
    return when {
        sending -> RemovalUiModel.Removing(NextJamCopy.REMOVING)

        removal.confirming == key -> RemovalUiModel.Confirming(
            prompt = NextJamCopy.removePrompt(song.title),
            details = removalDetails(song),
            confirmLabel = NextJamCopy.CONFIRM_REMOVE,
            cancelLabel = NextJamCopy.CANCEL,
            events = handler("confirming"),
        )

        else -> RemovalUiModel.Idle(NextJamCopy.REMOVE, handler("idle"))
    }
}

/** The confirmation's lines: assigned musicians (filled slots plus extras), then the published note. */
private fun RowAdmin.removalDetails(song: JamSong): List<String> {
    val assigned = song.lineup.slots.count { it.isFilled } + song.extraParticipants.size
    return listOfNotNull(
        if (assigned > 0) NextJamCopy.assignedMusicians(assigned) else null,
        if (jam.status == JamStatus.PUBLISHED) NextJamCopy.PUBLISHED_REMOVE_NOTE else null,
    )
}
