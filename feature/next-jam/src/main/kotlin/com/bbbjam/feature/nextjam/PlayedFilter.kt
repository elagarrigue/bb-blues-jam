package com.bbbjam.feature.nextjam

import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Lineup

/**
 * The lineup the instrument filter looks at (`song-played-state`, P8): a played song has no open slot to
 * offer, so for the filter's matches and chip counts its open slots do not exist. The song itself still
 * counts in "Todos" and still shows when no instrument is selected, and the musician filter looks at the
 * real lineup (who played what stays findable).
 */
internal fun JamSong.filterLineup(): Lineup = if (played) Lineup(lineup.slots.filter { it.isFilled }) else lineup
