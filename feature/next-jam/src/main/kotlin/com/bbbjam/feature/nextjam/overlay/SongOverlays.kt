package com.bbbjam.feature.nextjam.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.bbbjam.core.data.setlist.Assignment
import com.bbbjam.core.data.setlist.ExtraParticipantChange
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.data.setlist.LineupChange
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.data.setlist.SlotClear
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.feature.nextjam.AddFailureUiModel
import com.bbbjam.feature.nextjam.NextJamCopy
import com.bbbjam.feature.nextjam.admin.assignmentFailureMessage
import com.bbbjam.feature.nextjam.admin.failureMessage
import com.bbbjam.feature.nextjam.admin.keyFailureMessage
import com.bbbjam.feature.nextjam.admin.lineupFailureMessage
import com.bbbjam.feature.nextjam.admin.slotClearFailureMessage
import java.time.LocalDate

/**
 * The admin's song-level writes in flight and failed (`song-detail-replaces-row-expansion`): the
 * one overlay rule Próxima jam's rows and the song detail both apply, so a pending key, lineup,
 * assignment, clear or extras change draws the same values in both. Every value comes from
 * `Sending` entries only; a `Failed` entry never overlays, which is the revert, and becomes a card.
 * The entries are the repository's process-wide lists, so two presenters see the same state.
 * Musicians never get an overlay.
 */
internal class SongOverlays(
    val keyChanges: List<KeyChange> = emptyList(),
    val lineupChanges: List<LineupChange> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val slotClears: List<SlotClear> = emptyList(),
    val extraParticipantChanges: List<ExtraParticipantChange> = emptyList(),
) {
    /**
     * [song] as the admin sees it: the pending lineup counts, then [reserve] (the detail's
     * not-yet-recomposed `+`/`−` taps; identity by default), then pending assignments and clears,
     * and the pending extras. The key stays the cached one; see [pendingKey].
     */
    fun overlaid(date: LocalDate, song: JamSong, reserve: (Lineup) -> Lineup = { it }): JamSong = song.copy(
        lineup = reserve(lineupChanges.pendingLineup(date, song.songId, song.lineup))
            .overlayAssignments(date, song.songId, assignments)
            .overlaySlotClears(date, song.songId, slotClears),
        extraParticipants = extraParticipantChanges.pendingExtras(date, song.songId, song.extraParticipants),
    )

    /** The latest pending key of the song, or null: the cached key is drawn. */
    fun pendingKey(date: LocalDate, songId: SongId): Key? = keyChanges.pendingKey(date, songId)

    /** True while any song-level write of the song is sending: the "Guardando…" line. */
    fun isSaving(date: LocalDate, songId: SongId): Boolean = pendingKey(date, songId) != null ||
        lineupChanges.any { it.matches(date, songId) && it.state == LineupChange.State.Sending } ||
        assignments.any { it.jamDate == date && it.songId == songId && it.state == Assignment.State.Sending } ||
        slotClears.any { it.jamDate == date && it.songId == songId && it.state == SlotClear.State.Sending } ||
        isSavingExtras(date, songId)

    /** True while an add or removal of an "Otros" entry of the song is sending. */
    fun isSavingExtras(date: LocalDate, songId: SongId): Boolean = extraParticipantChanges.any {
        it.jamDate == date && it.songId == songId && it.state == ExtraParticipantChange.State.Sending
    }

    /**
     * The failed key, lineup, assignment, clear and extras changes of [date] (only [songId]'s when
     * given) as (id, title, message), unsorted; callers merge and sort by id (call order).
     */
    fun failures(date: LocalDate, songId: SongId? = null): List<OverlayFailure> {
        fun mine(entryDate: LocalDate, entryId: SongId) = entryDate == date && (songId == null || entryId == songId)
        val keys = keyChanges.filter { mine(it.jamDate, it.songId) }.mapNotNull { change ->
            (change.state as? KeyChange.State.Failed)?.let {
                OverlayFailure(change.id, NextJamCopy.keyFailed(change.title), keyFailureMessage(it.reason))
            }
        }
        val lineups = lineupChanges.filter { mine(it.jamDate, it.songId) }.mapNotNull { change ->
            (change.state as? LineupChange.State.Failed)?.let {
                OverlayFailure(change.id, NextJamCopy.lineupFailed(change.title), lineupFailureMessage(it.reason))
            }
        }
        val assigned = assignments.filter { mine(it.jamDate, it.songId) }.mapNotNull { assignment ->
            (assignment.state as? Assignment.State.Failed)?.let {
                OverlayFailure(
                    assignment.id,
                    NextJamCopy.assignmentFailed(assignment.musicianName.value, assignment.title),
                    assignmentFailureMessage(it.reason),
                )
            }
        }
        val clears = slotClears.filter { mine(it.jamDate, it.songId) }.mapNotNull { clear ->
            (clear.state as? SlotClear.State.Failed)?.let {
                OverlayFailure(clear.id, NextJamCopy.CLEAR_FAILED, slotClearFailureMessage(it.reason))
            }
        }
        val extras = extraParticipantChanges.filter { mine(it.jamDate, it.songId) }.mapNotNull { change ->
            (change.state as? ExtraParticipantChange.State.Failed)?.let {
                OverlayFailure(change.id, NextJamCopy.EXTRA_FAILED, failureMessage(it.reason))
            }
        }
        return keys + lineups + assigned + clears + extras
    }

    private fun LineupChange.matches(date: LocalDate, id: SongId) = jamDate == date && songId == id
}

/** One failed write, before it becomes an [AddFailureUiModel] card. */
internal data class OverlayFailure(val id: Long, val title: String, val message: String)

/** [failures] as dismissible cards in id (call) order; "Cerrar" calls [onDismiss] with the entry's id. */
internal fun failureCards(failures: List<OverlayFailure>, onDismiss: (Long) -> Unit): List<AddFailureUiModel> =
    failures.sortedBy { it.id }.map { (id, title, message) ->
        AddFailureUiModel(
            id = id,
            title = title,
            message = message,
            dismissLabel = NextJamCopy.CLOSE,
            events = EventHandler(key = id) { onDismiss(id) },
        )
    }

/** Collects the five song-level entry lists of [setlist] once (the flows are created once). */
@Composable
internal fun rememberSongOverlays(setlist: SetlistRepository): SongOverlays {
    val keyChanges by remember { setlist.observeKeyChanges() }.collectAsState(initial = emptyList())
    val lineupChanges by remember { setlist.observeLineupChanges() }.collectAsState(initial = emptyList())
    val assignments by remember { setlist.observeAssignments() }.collectAsState(initial = emptyList())
    val slotClears by remember { setlist.observeSlotClears() }.collectAsState(initial = emptyList())
    val extras by remember { setlist.observeExtraParticipantChanges() }.collectAsState(initial = emptyList())
    return SongOverlays(keyChanges, lineupChanges, assignments, slotClears, extras)
}

/** The latest sending change's list of extras, else the confirmed one. */
internal fun List<ExtraParticipantChange>.pendingExtras(
    date: LocalDate,
    songId: SongId,
    confirmed: List<ExtraParticipant>,
): List<ExtraParticipant> =
    filter { it.jamDate == date && it.songId == songId && it.state == ExtraParticipantChange.State.Sending }
        .maxByOrNull { it.id }?.extras ?: confirmed
