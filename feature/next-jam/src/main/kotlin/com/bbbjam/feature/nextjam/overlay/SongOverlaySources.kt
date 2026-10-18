package com.bbbjam.feature.nextjam.overlay

import com.bbbjam.core.data.setlist.SetlistAssignmentRepository
import com.bbbjam.core.data.setlist.SetlistExtraParticipantRepository
import com.bbbjam.core.data.setlist.SetlistKeyChangeRepository
import com.bbbjam.core.data.setlist.SetlistLineupRepository
import com.bbbjam.core.data.setlist.SetlistSlotClearRepository

/**
 * The five song-level contracts the song detail and the list both read and dismiss through
 * ([SongOverlays]), grouped so the presenters stay under detekt's parameter limit
 * (`setlist-mutation-contracts` D5). Each is the narrow contract, never `SetlistRepository`; the song
 * detail also writes through the lineup, clear and extra-participant ones.
 */
class SongOverlaySources(
    val keyRepository: SetlistKeyChangeRepository,
    val lineupRepository: SetlistLineupRepository,
    val assignmentRepository: SetlistAssignmentRepository,
    val slotRepository: SetlistSlotClearRepository,
    val extraRepository: SetlistExtraParticipantRepository,
)
