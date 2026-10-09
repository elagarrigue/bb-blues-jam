package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.setlist.ExtraParticipantChange
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.lineup.LineupPanelUiModel
import com.bbbjam.core.ui.lineup.toLineupPanel
import com.bbbjam.core.ui.presenter.EventHandler
import java.time.LocalDate

internal fun AdminState.extraEditor(date: LocalDate, song: JamSong): ExtraParticipantEditorUiModel {
    val extras = extraParticipantChanges.pendingExtras(date, song.songId, song.extraParticipants)
    return ExtraParticipantEditorUiModel(
        extras = extras,
        formVisible = extraFormKey == removalKey(date, song.songId),
        name = extraName,
        instrument = extraInstrument,
        saving = extraParticipantChanges.any {
            it.jamDate == date && it.songId == song.songId && it.state == ExtraParticipantChange.State.Sending
        },
        events = EventHandler(key = "${removalKey(date, song.songId)}|extras") { onExtraEvent(date, song.songId, it) },
    )
}

internal fun AdminState.lineupPanel(
    date: LocalDate,
    song: JamSong,
    extras: List<ExtraParticipant>,
): LineupPanelUiModel = song.lineup.toLineupPanel(
    extras = extras,
    onAssign = { instrument, position -> onAssignSlot(date, song.songId, instrument, position) },
    onClear = { instrument, position, name -> onClearSlot(date, song.songId, instrument, position, name) },
    canAssign = { instrument: Instrument, position: SlotPosition ->
        !slotClears.isClearing(date, song.songId, instrument, position.value)
    },
    canClear = { instrument: Instrument, position: SlotPosition ->
        !slotClears.isClearing(date, song.songId, instrument, position.value)
    },
    isClearing = { instrument: Instrument, position: SlotPosition ->
        slotClears.isClearing(date, song.songId, instrument, position.value)
    },
)

private fun List<ExtraParticipantChange>.pendingExtras(
    date: LocalDate,
    songId: SongId,
    confirmed: List<ExtraParticipant>,
) = filter { it.jamDate == date && it.songId == songId && it.state == ExtraParticipantChange.State.Sending }
    .maxByOrNull { it.id }?.extras ?: confirmed
