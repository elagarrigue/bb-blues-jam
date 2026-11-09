package com.bbbjam.feature.nextjam.overlay

import com.bbbjam.core.data.setlist.played.PlayedChange
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/*
 * The admin's optimistic "ya tocado" (`song-played-state`, P9), shared by Próxima jam and the song detail so
 * both draw the same value. Like the key, an optimistic value comes from `Sending` entries only: a failed or
 * outdated entry never overlays, so a failure **is** the revert to the confirmed value.
 */

/**
 * The value of the latest (highest id, so last called) played change of ([date], [songId]) still sending,
 * or null when none is: the confirmed value is drawn.
 */
internal fun List<PlayedChange>.pendingPlayed(date: LocalDate, songId: SongId): Boolean? =
    filter { it.jamDate == date && it.songId == songId && it.state == PlayedChange.State.Sending }
        .maxByOrNull { it.id }?.played
