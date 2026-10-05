package com.bbbjam.feature.songdetail

import com.bbbjam.core.ui.lineup.InstrumentGroupsUiModel
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel

/**
 * Everything the song detail draws, as plain values. Every state carries [back], so the back
 * control works whatever the screen shows. Read only: no admin control, no mutation.
 */
sealed interface SongDetailUiModel : UiModel {
    val back: BackUiModel

    /** Nothing emitted yet (a local read). Only the back control is drawn; [description] is for screen readers. */
    data class Loading(val description: String, override val back: BackUiModel) : SongDetailUiModel

    /** The jam or the row is gone, or the setlist is not shown: the empty block. Never an error. */
    data class NotFound(val empty: EmptyStateUiModel, override val back: BackUiModel) : SongDetailUiModel

    /**
     * One song of one jam. [artist] is null when blank. [key] is the jam song's key exactly as the
     * admin set it (D-08), drawn under [keyLabel]; [keyDescription] is what a screen reader says for
     * both ("Tonalidad Bm"). [lineup] is grouped by instrument, `Otros` last. Deliberately nothing
     * else: the catalog's optional fields are not part of the MVP detail (D-20, D-09).
     */
    data class Song(
        val title: String,
        val artist: String?,
        val keyLabel: String,
        val key: String,
        val keyDescription: String,
        val lineup: InstrumentGroupsUiModel,
        override val back: BackUiModel,
    ) : SongDetailUiModel
}
