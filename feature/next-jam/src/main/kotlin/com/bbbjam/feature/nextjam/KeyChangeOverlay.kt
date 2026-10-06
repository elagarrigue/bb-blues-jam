package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/*
 * The admin's optimistic key (`admin-set-key`, O1), shared by Próxima jam and the key picker so both
 * draw the same key. An optimistic value comes from `Sending` entries only: a failed entry never
 * overlays, so a failure **is** the revert to the cached, confirmed key.
 */

/**
 * The key of the latest (highest id, so last called) key change of ([date], [songId]) still sending,
 * or null when none is: the row then shows the cached key.
 */
internal fun List<KeyChange>.pendingKey(date: LocalDate, songId: SongId): Key? = filter {
    it.jamDate == date && it.songId == songId && it.state == KeyChange.State.Sending
}.maxByOrNull { it.id }?.key
