package com.bbbjam.feature.nextjam.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistMove
import com.bbbjam.core.data.setlist.SetlistRemove
import com.bbbjam.core.model.SongId
import com.bbbjam.feature.nextjam.overlay.SongOverlays
import com.bbbjam.feature.nextjam.refresh.AdminRefreshEffect
import java.time.LocalDate

/**
 * The list's admin state, or null for a musician: the per-login refresh, the confirming removal,
 * the entry lists the rows overlay ([overlays], shared with the song
 * detail). Song-level controls live in the song detail (`song-detail-replaces-row-expansion`).
 */
@Composable
internal fun rememberNextJamAdminState(
    adminSession: AdminSession,
    jams: JamsRepository,
    writes: NextJamSetlistWrites,
    isAdmin: Boolean,
    adds: List<SetlistAdd>,
    removes: List<SetlistRemove>,
    overlays: SongOverlays,
    moves: List<SetlistMove>,
    onMove: (LocalDate, SongId, Int) -> Unit,
    onAddSong: (LocalDate) -> Unit,
): AdminState? {
    AdminRefreshEffect(adminSession, jams)
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val onRemoval = removalHandler(writes.removals, scope, { confirming == it }) { confirming = it }
    if (!isAdmin) return null
    return AdminState(
        adds,
        onAddSong = onAddSong,
        onDismissAdd = writes.adds::dismissAdd,
        removal = RemovalState(removes, confirming, onRemoval),
        keyChanges = overlays.keyChanges,
        assignments = overlays.assignments,
        extraParticipantChanges = overlays.extraParticipantChanges,
        slotClears = overlays.slotClears,
        moves = moves,
        lineupChanges = overlays.lineupChanges,
        onMove = onMove,
        onDismissRemove = writes.removals::dismissRemove,
        onDismissMove = writes.moves::dismissMove,
        overlaySources = overlays.sources,
    )
}
