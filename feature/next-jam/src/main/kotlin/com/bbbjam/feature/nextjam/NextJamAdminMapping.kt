package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.setlist.Assignment
import com.bbbjam.core.data.setlist.ExtraParticipantChange
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.data.setlist.LineupChange
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistMove
import com.bbbjam.core.data.setlist.SetlistRemove
import com.bbbjam.core.data.setlist.SlotClear
import com.bbbjam.core.model.Jam as DomainJam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.status.jamStatusBadge
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
 * admin callbacks, the [removal] state, the [moves] and the song-level entry lists the rows overlay
 * ([overlays], the rule shared with the song detail). Null for musicians. Since
 * `song-detail-replaces-row-expansion` the song-level controls (key, lineup, slots, extras) live in
 * the song detail, not here.
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
    val lineupChanges: List<LineupChange> = emptyList(),
    val onMove: (LocalDate, SongId, Int) -> Unit = { _, _, _ -> },
) {
    /** The song-level overlay rule this list shares with the song detail. */
    val overlays: SongOverlays
        get() = SongOverlays(keyChanges, lineupChanges, assignments, slotClears, extraParticipantChanges)
}

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
    return NextJamAdminUiModel(
        status = AdminStatusUiModel(
            badge = jamStatusBadge(jam.status),
            note = when (jam.status) {
                JamStatus.DRAFT -> NextJamCopy.DRAFT_NOTE
                JamStatus.CREATED -> NextJamCopy.CREATED_NOTE
                JamStatus.PUBLISHED -> NextJamCopy.PUBLISHED_NOTE
            },
            hint = NextJamCopy.STATUS_HINT,
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
 * The failed adds, removals and moves of [date], plus every song-level failure ([SongOverlays]), as
 * cards in id order (call order). A key change has its own message mapping ([keyFailureMessage]).
 */
private fun AdminState.failures(date: LocalDate): List<AddFailureUiModel> {
    val failedAdds = adds.filter { it.jamDate == date }.mapNotNull { add ->
        (add.state as? SetlistAdd.State.Failed)?.let {
            OverlayFailure(add.id, NextJamCopy.addFailed(add.title), failureMessage(it.reason))
        }
    }
    val failedRemoves = removal.removes.filter { it.jamDate == date }.mapNotNull { remove ->
        (remove.state as? SetlistRemove.State.Failed)?.let {
            OverlayFailure(remove.id, NextJamCopy.removeFailed(remove.title), failureMessage(it.reason))
        }
    }
    val failedMoves = moves.filter { it.jamDate == date }.mapNotNull { move ->
        (move.state as? SetlistMove.State.Failed)?.let {
            OverlayFailure(move.id, MoveCopy.failed(move.title), moveFailureMessage(it.reason))
        }
    }
    return failureCards(failedAdds + failedRemoves + failedMoves + overlays.failures(date), onDismiss)
}

/**
 * The admin's [row] of [song] (already overlaid by [SongOverlays]): the latest pending key change
 * drawn in place of the cached key (O1; a failed change never overlays, which is the revert),
 * "Guardando…" while any write of the song is sending, and the compact bar's move and removal.
 */
internal fun RowAdmin.decorate(row: SongRowUiModel, song: JamSong, total: Int): SongRowUiModel {
    val pending = state.overlays.pendingKey(jam.date, song.songId)
    val removal = removalModel(song)
    val saving = isMoveSending(song) || state.overlays.isSaving(jam.date, song.songId)
    return row.copy(
        key = pending?.value ?: row.key,
        keyDescription = if (pending == null) row.keyDescription else SongListText.keyDescription(pending.value),
        admin = SongRowAdminUiModel(
            removal = removal,
            move = moveModel(row, song, total, removal),
            saveStatus = if (saving) NextJamCopy.SAVING else null,
        ),
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
            confirmLabel = NextJamCopy.OK,
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
