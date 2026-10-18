package com.bbbjam.feature.nextjam.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.bbbjam.core.data.setlist.Assignment
import com.bbbjam.core.data.setlist.ExtraParticipantChange
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.data.setlist.LineupChange
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
    val sources: SongOverlaySources? = null,
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
                OverlayFailure(
                    change.id,
                    NextJamCopy.keyFailed(change.title),
                    keyFailureMessage(it.reason),
                    sources.dismissOf { keyRepository::dismissKeyChange },
                )
            }
        }
        val lineups = lineupChanges.filter { mine(it.jamDate, it.songId) }.mapNotNull { change ->
            (change.state as? LineupChange.State.Failed)?.let {
                OverlayFailure(
                    change.id,
                    NextJamCopy.lineupFailed(change.title),
                    lineupFailureMessage(it.reason),
                    sources.dismissOf { lineupRepository::dismissLineupChange },
                )
            }
        }
        val assigned = assignments.filter { mine(it.jamDate, it.songId) }.mapNotNull { assignment ->
            (assignment.state as? Assignment.State.Failed)?.let {
                OverlayFailure(
                    assignment.id,
                    NextJamCopy.assignmentFailed(assignment.musicianName.value, assignment.title),
                    assignmentFailureMessage(it.reason),
                    sources.dismissOf { assignmentRepository::dismissAssignment },
                )
            }
        }
        val clears = slotClears.filter { mine(it.jamDate, it.songId) }.mapNotNull { clear ->
            (clear.state as? SlotClear.State.Failed)?.let {
                OverlayFailure(
                    clear.id,
                    NextJamCopy.CLEAR_FAILED,
                    slotClearFailureMessage(it.reason),
                    sources.dismissOf { slotRepository::dismissSlotClear },
                )
            }
        }
        val extras = extraParticipantChanges.filter { mine(it.jamDate, it.songId) }.mapNotNull { change ->
            (change.state as? ExtraParticipantChange.State.Failed)?.let {
                OverlayFailure(
                    change.id,
                    NextJamCopy.EXTRA_FAILED,
                    failureMessage(it.reason),
                    sources.dismissOf { extraRepository::dismissExtraParticipantChange },
                )
            }
        }
        return keys + lineups + assigned + clears + extras
    }

    private fun LineupChange.matches(date: LocalDate, id: SongId) = jamDate == date && songId == id
}

/** The dismiss of the contract that owns the entry, or none when the overlays were built from lists only. */
private fun SongOverlaySources?.dismissOf(dismiss: SongOverlaySources.() -> (Long) -> Unit): (Long) -> Unit =
    this?.dismiss() ?: {}

/**
 * One failed write, before it becomes an [AddFailureUiModel] card. [dismiss] is the dismiss of the
 * contract that owns the entry (D6 of `setlist-mutation-contracts`), called with [id].
 */
internal data class OverlayFailure(val id: Long, val title: String, val message: String, val dismiss: (Long) -> Unit)

/** [failures] as dismissible cards in id (call) order; "Cerrar" calls each failure's own dismiss with its id. */
internal fun failureCards(failures: List<OverlayFailure>): List<AddFailureUiModel> =
    failures.sortedBy { it.id }.map { failure ->
        AddFailureUiModel(
            id = failure.id,
            title = failure.title,
            message = failure.message,
            dismissLabel = NextJamCopy.CLOSE,
            events = EventHandler(key = failure.id) { failure.dismiss(failure.id) },
        )
    }

/** Collects the five song-level entry lists of [sources] once (the flows are created once). */
@Composable
internal fun rememberSongOverlays(sources: SongOverlaySources): SongOverlays {
    val keyChanges by remember { sources.keyRepository.observeKeyChanges() }.collectAsState(initial = emptyList())
    val lineupChanges by remember {
        sources.lineupRepository.observeLineupChanges()
    }.collectAsState(initial = emptyList())
    val assignments by remember {
        sources.assignmentRepository.observeAssignments()
    }.collectAsState(initial = emptyList())
    val slotClears by remember { sources.slotRepository.observeSlotClears() }.collectAsState(initial = emptyList())
    val extras by remember {
        sources.extraRepository.observeExtraParticipantChanges()
    }.collectAsState(initial = emptyList())
    return SongOverlays(keyChanges, lineupChanges, assignments, slotClears, extras, sources)
}

/** The latest sending change's list of extras, else the confirmed one. */
internal fun List<ExtraParticipantChange>.pendingExtras(
    date: LocalDate,
    songId: SongId,
    confirmed: List<ExtraParticipant>,
): List<ExtraParticipant> =
    filter { it.jamDate == date && it.songId == songId && it.state == ExtraParticipantChange.State.Sending }
        .maxByOrNull { it.id }?.extras ?: confirmed
