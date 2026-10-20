package com.bbbjam.core.data.catalog.edit

import com.bbbjam.core.model.SongId

/**
 * The admin's writes to the song catalog (`admin-catalog-tab`, D-30 amending D-04, D-13): add a song,
 * edit its title, artist and default key, delete it. Every call goes through the admin POST path, where
 * Apps Script validates it with the passphrase. Room changes only when the server echoes what it wrote
 * (no optimistic row), and the catalog is refreshed afterwards. Calls are serialized in call order and
 * run in the data scope, so cancelling the caller never cancels a write already started. The `tempo`,
 * `etiquetas`, `dificultad` and `songsterr_id` columns and every id stay Sheet-only.
 */
interface CatalogEditRepository {
    /** Adds [fields] as a new song; the server generates the id and never takes one from the client. */
    suspend fun createSong(fields: SongFields): CreateSongOutcome

    /** Sets the title, artist and default key of [id]; the id never changes. */
    suspend fun updateSong(id: SongId, fields: SongFields): UpdateSongOutcome

    /**
     * Deletes [id]. The server refuses (`song_in_future_jam`) while the song is in the upcoming jam or a
     * held-back one; a song only in past jams can go, their tabs keep plain-text copies.
     */
    suspend fun deleteSong(id: SongId): DeleteSongOutcome
}
