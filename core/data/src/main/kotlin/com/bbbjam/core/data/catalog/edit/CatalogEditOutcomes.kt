package com.bbbjam.core.data.catalog.edit

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId

/** The three fields of a catalog song the app writes (D-30); the title and artist are normalized. */
data class SongFields(val title: String, val artist: String, val defaultKey: Key)

/** The result of adding a song (`admin-catalog-tab`). */
sealed interface CreateSongOutcome {
    /** The server appended the row and generated [id]; Room has the song. */
    data class Created(val id: SongId) : CreateSongOutcome

    /** Nothing was added; [reason] is the write outcome (`Rejected("song_exists")`, `Offline`...). */
    data class NotCreated(val reason: WriteOutcome) : CreateSongOutcome

    /** The deployed script does not know `createSong` yet: the user has to update it (migration guide). */
    data object ScriptOutdated : CreateSongOutcome
}

/** The result of editing a song (`admin-catalog-tab`). */
sealed interface UpdateSongOutcome {
    /** The server wrote and read back the song; [changed] is false when nothing differed. */
    data class Updated(val changed: Boolean) : UpdateSongOutcome

    /** Nothing changed; [reason] is the write outcome. */
    data class NotUpdated(val reason: WriteOutcome) : UpdateSongOutcome

    /** The deployed script does not know `updateSong` yet. */
    data object ScriptOutdated : UpdateSongOutcome
}

/** The result of deleting a song (`admin-catalog-tab`). */
sealed interface DeleteSongOutcome {
    /** The server deleted the row; Room dropped the song. */
    data object Deleted : DeleteSongOutcome

    /** Nothing was deleted; [reason] is the write outcome (`Rejected("song_in_future_jam")`, `Offline`...). */
    data class NotDeleted(val reason: WriteOutcome) : DeleteSongOutcome

    /** The deployed script does not know `deleteSong` yet. */
    data object ScriptOutdated : DeleteSongOutcome
}
