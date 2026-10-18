package com.bbbjam.feature.nextjam.songdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.feature.nextjam.ExtraParticipantEditorUiModel
import com.bbbjam.feature.nextjam.LineupEditorLineUiModel
import com.bbbjam.feature.nextjam.overlay.SongOverlaySources
import com.bbbjam.feature.nextjam.overlay.SongOverlays
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * What the admin's song-level controls need (`song-detail-replaces-row-expansion`): the shared
 * [overlays], the always-visible lineup editor, its `+`/`−` handler and the immediate
 * [reservations]), the "Otros" form, and the navigation and write callbacks for the key picker,
 * the assign screen, "Liberar" and "Cerrar". The song detail holds one; every handler is keyed by
 * (date, songId, step), as on the list.
 */
internal class SongAdminState(
    val overlays: SongOverlays = SongOverlays(),
    val onSetKey: (LocalDate, SongId) -> Unit = { _, _ -> },
    val reservations: LineupReservations,
    val onLineupCount: (LocalDate, SongId, Instrument, LineupEditorLineUiModel.Event) -> Unit = { _, _, _, _ -> },
    val extraFormKey: String? = null,
    val extraName: String = "",
    val extraInstrument: String = "",
    val onExtraEvent: (LocalDate, SongId, ExtraParticipantEditorUiModel.Event) -> Unit = { _, _, _ -> },
    val onAssignSlot: (LocalDate, SongId, Instrument, SlotPosition) -> Unit = { _, _, _, _ -> },
    val onClearSlot: (LocalDate, SongId, Instrument, SlotPosition, String) -> Unit = { _, _, _, _, _ -> },
)

/**
 * The song detail's admin state, or null for a musician. Every state object is created once and
 * never recreated, so an earlier model's handler writes through the same state; writes launch
 * undispatched, so the `Sending` entry exists before the next frame and leaving the screen never
 * cancels one (they run in `DataScope`).
 */
@Composable
internal fun rememberSongAdminState(
    sources: SongOverlaySources,
    snapshot: JamsSnapshot?,
    isAdmin: Boolean,
    overlays: SongOverlays,
    onSetKey: (LocalDate, SongId) -> Unit,
    onAssignSlot: (LocalDate, SongId, Instrument, SlotPosition) -> Unit,
): SongAdminState? {
    val lineup = rememberLineupControls(sources.lineupRepository, snapshot, isAdmin)
    val extraFormKey = rememberSaveable { mutableStateOf<String?>(null) }
    val extraName = rememberSaveable { mutableStateOf("") }
    val extraInstrument = rememberSaveable { mutableStateOf("") }
    val extraAddConsumed = remember { mutableStateOf(false) }
    val extraForm =
        remember(sources) { ExtraParticipantFormState(extraFormKey, extraName, extraInstrument, extraAddConsumed) }
    val scope = rememberCoroutineScope()
    if (!isAdmin) return null
    return SongAdminState(
        overlays = overlays,
        onSetKey = onSetKey,
        reservations = lineup.reservations,
        onLineupCount = lineup.onLineupCount,
        extraFormKey = extraFormKey.value,
        extraName = extraName.value,
        extraInstrument = extraInstrument.value,
        onExtraEvent = { date, songId, event -> extraForm.handle(date, songId, event, sources.extraRepository, scope) },
        onAssignSlot = onAssignSlot,
        onClearSlot = { date, songId, instrument, position, name ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                sources.slotRepository.clearSlot(date, songId, instrument, position, name)
            }
        },
    )
}
