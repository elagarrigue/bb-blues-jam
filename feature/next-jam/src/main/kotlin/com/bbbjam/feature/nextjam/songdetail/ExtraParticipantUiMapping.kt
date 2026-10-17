package com.bbbjam.feature.nextjam.songdetail

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.ui.lineup.LineupPanelUiModel
import com.bbbjam.core.ui.lineup.toLineupPanel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.feature.nextjam.ExtraParticipantEditorUiModel
import com.bbbjam.feature.nextjam.NextJamCopy
import com.bbbjam.feature.nextjam.admin.removalKey
import com.bbbjam.feature.nextjam.overlay.isClearing
import java.time.LocalDate

internal fun SongAdminState.extraEditor(date: LocalDate, song: JamSong): ExtraParticipantEditorUiModel {
    val formVisible = extraFormKey == removalKey(date, song.songId)
    return ExtraParticipantEditorUiModel(
        formVisible = formVisible,
        name = extraName,
        instrument = extraInstrument,
        submitEnabled = formVisible && isSubmittable(extraName, extraInstrument),
        saving = overlays.isSavingExtras(date, song.songId),
        rowLabel = NextJamCopy.OTHER,
        actionLabel = NextJamCopy.SUBMIT_EXTRA,
        actionDescription = NextJamCopy.ADD_EXTRA,
        events = EventHandler(key = "${removalKey(date, song.songId)}|extras") { onExtraEvent(date, song.songId, it) },
    )
}

internal fun SongAdminState.lineupPanel(
    date: LocalDate,
    song: JamSong,
    extras: List<ExtraParticipant>,
): LineupPanelUiModel = song.lineup.toLineupPanel(
    extras = extras,
    onAssign = { instrument, position -> onAssignSlot(date, song.songId, instrument, position) },
    onRemoveExtra = { ordinal, extra ->
        onExtraEvent(
            date,
            song.songId,
            ExtraParticipantEditorUiModel.Event.Remove(ordinal, extra.name, extra.instrument),
        )
    },
    onClear = { instrument, position, name -> onClearSlot(date, song.songId, instrument, position, name) },
    canAssign = { instrument: Instrument, position: SlotPosition ->
        !overlays.slotClears.isClearing(date, song.songId, instrument, position.value)
    },
    canClear = { instrument: Instrument, position: SlotPosition ->
        !overlays.slotClears.isClearing(date, song.songId, instrument, position.value)
    },
    isClearing = { instrument: Instrument, position: SlotPosition ->
        overlays.slotClears.isClearing(date, song.songId, instrument, position.value)
    },
).copy(hint = null) // The hint addresses musicians; the admin is the organization (U5).
