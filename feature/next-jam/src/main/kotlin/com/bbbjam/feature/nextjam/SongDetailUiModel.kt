package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.lineup.LineupPanelUiModel
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel

/**
 * Everything the song detail draws, as plain values. Every state carries [back], so the back
 * control works whatever the screen shows. For musicians it is read only; for the admin
 * (`song-detail-replaces-row-expansion`, D-15) the song gains [Song.admin], the song-level controls
 * the expanded row used to hold.
 */
sealed interface SongDetailUiModel : UiModel {
    val back: BackUiModel

    /**
     * Nothing to draw yet: the jams or the admin flag are still being read (so the admin never sees a
     * not-found frame for a draft's song). Only the back control is drawn; [description] is for
     * screen readers.
     */
    data class Loading(val description: String, override val back: BackUiModel) : SongDetailUiModel

    /** The jam or the row is gone, or the setlist is not shown: the empty block. Never an error. */
    data class NotFound(val empty: EmptyStateUiModel, override val back: BackUiModel) : SongDetailUiModel

    /**
     * One song of one jam. [artist] is null when blank. [key] is the jam song's key exactly as the
     * admin set it (D-08), drawn under [keyLabel]; [keyDescription] is what a screen reader says for
     * both ("Tonalidad Bm"); for the admin both show a pending key change. [lineup] is the lineup
     * panel (open slots first, then filled, then `Otros`; P1), with the admin's line actions and no
     * hint for the admin. [admin] is null for musicians. Deliberately nothing else: the catalog's
     * optional fields are not part of the MVP detail (D-20, D-09).
     */
    data class Song(
        val title: String,
        val artist: String?,
        val keyLabel: String,
        val key: String,
        val keyDescription: String,
        val lineup: LineupPanelUiModel,
        override val back: BackUiModel,
        val admin: SongDetailAdminUiModel? = null,
    ) : SongDetailUiModel
}

/**
 * The admin's controls of one song on its detail, drawn after what a musician sees in each block:
 * [saveStatus] ("Guardando…" while any song-level write of it is sending), [setKey] ("Cambiar
 * tonalidad", opens the key picker), [lineup] ("Cambiar formación" or the inline editor),
 * [extraParticipants] ("Agregar a Otros" or its form) and [failures], the failed writes of this song
 * only, each with "Cerrar". "Anotar", "Liberar" and an extra's "Quitar" are line actions of the
 * lineup panel.
 */
data class SongDetailAdminUiModel(
    val saveStatus: String?,
    val setKey: SetKeyActionUiModel,
    val lineup: LineupEditorUiModel,
    val extraParticipants: ExtraParticipantEditorUiModel,
    val failures: List<AddFailureUiModel>,
) : UiModel
