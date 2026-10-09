package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.setlist.Assignment
import com.bbbjam.core.data.setlist.ExtraParticipantChange
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistMove
import com.bbbjam.core.data.setlist.SetlistPublish
import com.bbbjam.core.data.setlist.SetlistRemove
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.data.setlist.SlotClear
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

@Composable
internal fun rememberNextJamAdminState(
    adminSession: AdminSession,
    jams: JamsRepository,
    setlist: SetlistRepository,
    isAdmin: Boolean,
    adds: List<SetlistAdd>,
    removes: List<SetlistRemove>,
    keyChanges: List<KeyChange>,
    assignments: List<Assignment>,
    extraParticipantChanges: List<ExtraParticipantChange>,
    slotClears: List<SlotClear>,
    moves: List<SetlistMove>,
    publishes: List<SetlistPublish>,
    lineup: LineupControls,
    onMove: (LocalDate, SongId, Int) -> Unit,
    onAddSong: (LocalDate) -> Unit,
    onSetKey: (LocalDate, SongId) -> Unit,
    onAssignSlot: (LocalDate, SongId, Instrument, SlotPosition) -> Unit,
): AdminState? {
    rememberAdminRefresh(adminSession, jams)
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    val publishControls = rememberPublishControls(setlist)
    val extraFormKey = rememberSaveable { mutableStateOf<String?>(null) }
    val extraName = rememberSaveable { mutableStateOf("") }
    val extraInstrument = rememberSaveable { mutableStateOf("") }
    val extraAddConsumed = remember { mutableStateOf(false) }
    val extraForm =
        remember(setlist) { ExtraParticipantFormState(extraFormKey, extraName, extraInstrument, extraAddConsumed) }
    val scope = rememberCoroutineScope()
    val onRemoval = removalHandler(setlist, scope, { confirming == it }) { confirming = it }
    if (!isAdmin) return null
    return AdminState(
        adds,
        onAddSong = onAddSong,
        onDismiss = setlist::dismiss,
        removal = RemovalState(removes, confirming, onRemoval),
        keyChanges = keyChanges,
        assignments = assignments,
        extraParticipantChanges = extraParticipantChanges,
        slotClears = slotClears,
        moves = moves,
        lineupChanges = lineup.lineupChanges,
        lineupEditing = lineup.lineupEditing,
        reservations = lineup.reservations,
        onLineupEditor = lineup.onLineupEditor,
        onLineupCount = lineup.onLineupCount,
        onSetKey = onSetKey,
        extraFormKey = extraFormKey.value,
        extraName = extraName.value,
        extraInstrument = extraInstrument.value,
        onExtraEvent = { date, songId, event -> extraForm.handle(date, songId, event, setlist, scope) },
        onAssignSlot = onAssignSlot,
        onClearSlot = { date, songId, instrument, position, name ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                setlist.clearSlot(date, songId, instrument, position, name)
            }
        },
        onMove = onMove,
        publishes = publishes,
        publishConfirming = publishControls.confirming,
        onRequestPublish = publishControls.onRequest,
        onCancelPublish = publishControls.onCancel,
        onConfirmPublish = publishControls.onConfirm,
        onRetryPublish = publishControls.onRetry,
    )
}
