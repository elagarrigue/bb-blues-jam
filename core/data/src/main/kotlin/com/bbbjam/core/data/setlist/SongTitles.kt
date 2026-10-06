package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * The title the setlist shows for [songId] of [jamDate], resolved as the `jam_song_resolved` view
 * does: the cached catalog's, else the tab's copy, else the id itself. Shared by the removal and the
 * key change entries.
 */
internal suspend fun displayedTitle(
    catalogDao: CatalogDao,
    setlistDao: SetlistDao,
    jamDate: LocalDate,
    songId: SongId,
): String = catalogDao.song(songId.value)?.title
    ?: setlistDao.songsOf(jamDate.toString()).firstOrNull { it.songId == songId.value }?.titleCopy
    ?: songId.value
